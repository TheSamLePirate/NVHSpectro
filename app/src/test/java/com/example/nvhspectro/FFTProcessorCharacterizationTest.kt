package com.example.nvhspectro

import com.example.nvhspectro.testutil.SynthSignals
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Characterization tests for [FFTProcessor] — Phase 0.6 of the AAA plan.
 *
 * These tests serve two purposes:
 *  1. Verify the analytically-correct behavior (amplitude calibration) so that
 *     Phase 1/3 refactors are provably safe.
 *  2. PIN current behavior that the audit identified as defective (first-frame
 *     squelch D3, sub-30 Hz masking D7, scalloping D9). Those tests carry the
 *     finding ID; when the defect is fixed in its planned phase, the pinned
 *     test is UPDATED IN THE SAME COMMIT as the fix.
 */
class FFTProcessorCharacterizationTest {

    private val fftSize = 2048
    private val sampleRate = 44100
    private val df = sampleRate.toDouble() / fftSize // ≈ 21.53 Hz

    // ---------------------------------------------------------------
    // Amplitude calibration — the one thing with an exact expected value
    // ---------------------------------------------------------------

    @Test
    fun fullScaleSine_atBinCenter_readsZeroDbfs() {
        val bin = 100 // ≈ 2153 Hz — well above the 30 Hz mask
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sampleRate)
        val mags = FFTProcessor(fftSize).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 1.0)
        )
        // Hann coherent-gain normalization mag/(N/4): full-scale sine = 0 dBFS.
        assertEquals(0.0, mags[bin], 0.05)
    }

    @Test
    fun minus20dbSine_reads_minus20Dbfs() {
        val bin = 300 // ≈ 6460 Hz
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sampleRate)
        val mags = FFTProcessor(fftSize).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 0.1)
        )
        assertEquals(-20.0, mags[bin], 0.05)
    }

    @Test
    fun amplitudeLinearity_minus60Dbfs() {
        val bin = 200
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sampleRate)
        val mags = FFTProcessor(fftSize).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 0.001)
        )
        // 16-bit quantization noise dominates at low amplitude; wider tolerance.
        assertEquals(-60.0, mags[bin], 0.2)
    }

    // ---------------------------------------------------------------
    // Pinned current behavior (audit findings — update when fixed)
    // ---------------------------------------------------------------

    /** [audit D7] Bins below 30 Hz are hard-masked to -120 inside processFFT. */
    @Test
    fun pinned_binsBelow30Hz_forcedToMinus120() {
        val mags = FFTProcessor(fftSize).processFFT(
            SynthSignals.sine(21.5, sampleRate, fftSize, amplitude = 1.0)
        )
        assertEquals(-120.0, mags[0], 0.0)
        assertEquals(-120.0, mags[1], 0.0) // 21.5 Hz < 30 Hz
        assertTrue("bin 2 (43 Hz) must NOT be masked", mags[2] > -120.0)
    }

    /**
     * [audit D9] No scalloping correction: a tone at bin+0.5 reads ~1.42 dB low
     * on both straddling bins. Documents the ±1.4 dB order-trace ripple.
     */
    @Test
    fun pinned_halfBinTone_showsHannScallopingLoss() {
        val bin = 150
        val freq = (bin + 0.5) * df
        val mags = FFTProcessor(fftSize).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 1.0)
        )
        val peak = maxOf(mags[bin], mags[bin + 1])
        assertTrue("expected ≈ -1.42 dB scalloping, got $peak", peak in -1.9..-1.0)
    }

    /**
     * [audit D3] lastFrameEnergyDb initializes to -120, so the very first frame
     * of every stream trips the >6 dB anti-shock detector and is squelched:
     * the analysis provably starts with a discarded frame.
     */
    @Test
    fun pinned_firstTtnrFrame_alwaysSquelchedAsShock() {
        val p = FFTProcessor(fftSize)
        val ttnr = p.computeTTNR(toneOverFloorSpectrum(), sampleRate)
        assertTrue(
            "first frame must report no emergence (all ≤ 0), got max ${ttnr.max()}",
            ttnr.max() <= 0.0
        )
    }

    @Test
    fun steadyTone_detectedAfterWarmup() {
        val p = FFTProcessor(fftSize)
        val spectrum = toneOverFloorSpectrum()
        var last = DoubleArray(0)
        repeat(12) { last = p.computeTTNR(spectrum, sampleRate) }
        assertTrue(
            "tone at bin 200 should exceed 10 dB TTNR after warmup, got ${last[200]}",
            last[200] > 10.0
        )
    }

    @Test
    fun noiseOnlyFloor_reportsNoEmergence() {
        val p = FFTProcessor(fftSize)
        val flat = DoubleArray(fftSize / 2) { -80.0 }
        var last = DoubleArray(0)
        repeat(5) { last = p.computeTTNR(flat, sampleRate) }
        assertTrue("flat floor must yield no emergence, got max ${last.max()}", last.max() <= 0.0)
    }

    // ---------------------------------------------------------------
    // Golden snapshot — refactor safety net
    // ---------------------------------------------------------------

    /**
     * Full-spectrum snapshot of a fixed noise+tone mixture. FFT math is
     * deterministic, so any refactor that changes a single output value fails
     * here. If the golden file is missing (first local run), it is recorded to
     * src/test/resources and the test fails asking for a re-run + commit.
     */
    @Test
    fun goldenSpectrum_seed42_matchesSnapshot() {
        val input = SynthSignals.mix(
            SynthSignals.sine(2153.3, sampleRate, fftSize, amplitude = 0.3),
            SynthSignals.seededNoise(fftSize, seed = 42L, amplitude = 0.05)
        )
        val mags = FFTProcessor(fftSize).processFFT(input)

        val resource = javaClass.getResourceAsStream("/golden/fft_seed42.csv")
        if (resource == null) {
            val out = File("src/test/resources/golden/fft_seed42.csv")
            out.parentFile.mkdirs()
            out.writeText(mags.joinToString("\n") { "%.12e".format(java.util.Locale.US, it) })
            fail("Golden recorded to ${out.absolutePath} — re-run tests and commit the file.")
        }
        val golden = resource!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.toDouble() }
        assertEquals("golden length", golden.size, mags.size)
        for (i in mags.indices) {
            assertEquals("bin $i", golden[i], mags[i], 1e-9)
        }
    }

    // ---------------------------------------------------------------

    /** -80 dBFS floor with a strict-local-peak tone at bin 200 (≈4306 Hz). */
    private fun toneOverFloorSpectrum(): DoubleArray {
        val s = DoubleArray(fftSize / 2) { -80.0 }
        s[199] = -53.0
        s[200] = -50.0
        s[201] = -53.0
        return s
    }
}
