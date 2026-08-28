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
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 1.0)
        )
        // Hann coherent-gain normalization mag/(N/4): full-scale sine = 0 dBFS.
        assertEquals(0.0, mags[bin], 0.05)
    }

    @Test
    fun minus20dbSine_reads_minus20Dbfs() {
        val bin = 300 // ≈ 6460 Hz
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sampleRate)
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 0.1)
        )
        assertEquals(-20.0, mags[bin], 0.05)
    }

    /**
     * [C1, plan 1.1] The sample rate is now threaded per instance: a tone in a
     * 48 kHz stream must land on the 48 kHz bin grid, and the 30 Hz mask must
     * follow the real bin width (Δf = 23.44 Hz), not an assumed 44.1 kHz grid.
     */
    @Test
    fun c1_fortyEightKhzStream_usesItsOwnBinGrid() {
        val sr48 = 48000
        val bin = 171 // 171 × 48000/2048 = 4007.8125 Hz
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sr48)
        val mags = FFTProcessor(fftSize, sr48).processFFT(
            SynthSignals.sine(freq, sr48, fftSize, amplitude = 1.0)
        )
        assertEquals(0.0, mags[bin], 0.05)
        // Under the old hard-coded 44100, this tone would have been attributed
        // to bin round(4007.8 × 2048/44100) = 186 — verify 186 is NOT the peak.
        assertTrue("44.1k-grid bin must not carry the tone", mags[186] < -20.0)
        // A full-scale tone AT bin 2 (46.9 Hz) reads correctly on the 48 kHz grid.
        val lowMags = FFTProcessor(fftSize, sr48).processFFT(
            SynthSignals.sine(SynthSignals.binCenteredFreq(2, fftSize, sr48), sr48, fftSize, amplitude = 1.0)
        )
        assertEquals(0.0, lowMags[2], 0.3)
    }

    @Test
    fun amplitudeLinearity_minus60Dbfs() {
        val bin = 200
        val freq = SynthSignals.binCenteredFreq(bin, fftSize, sampleRate)
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 0.001)
        )
        // 16-bit quantization noise dominates at low amplitude; wider tolerance.
        assertEquals(-60.0, mags[bin], 0.2)
    }

    // ---------------------------------------------------------------
    // Pinned current behavior (audit findings — update when fixed)
    // ---------------------------------------------------------------

    /**
     * [D7, plan 3.7 — FIXED] Sub-30 Hz masking moved to the display layer:
     * the FFT now reports TRUE magnitudes everywhere. A 21.5 Hz full-scale
     * tone reads its real level at bin 1 instead of a destroyed −120.
     */
    @Test
    fun d7_lowBins_carryTrueData_maskIsDisplayPolicyOnly() {
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(
            SynthSignals.sine(21.5, sampleRate, fftSize, amplitude = 1.0)
        )
        assertTrue("21.5 Hz tone must carry real energy at bin 1, got ${mags[1]}", mags[1] > -3.0)
    }

    /**
     * [D9] RAW bin readout keeps its physical ~1.42 dB scalloping (that is
     * what an FFT bin measures); the CORRECTION lives in the tracked-order
     * readout — see d9_trackedOrderReadout_correctsScalloping in
     * OrderTrackingEngineTest [plan 3.7].
     */
    @Test
    fun d9_rawBinReadout_hasPhysicalScalloping() {
        val bin = 150
        val freq = (bin + 0.5) * df
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(
            SynthSignals.sine(freq, sampleRate, fftSize, amplitude = 1.0)
        )
        val peak = maxOf(mags[bin], mags[bin + 1])
        assertTrue("expected ≈ -1.42 dB scalloping, got $peak", peak in -1.9..-1.0)
    }

    /**
     * [D3, plan 3.7 — FIXED] The first frame of a stream is ANALYZED: with no
     * previous frame there is no shock reference (the historical −120
     * initialization squelched frame 1 of every stream unconditionally).
     */
    @Test
    fun d3_firstFrame_isAnalyzed_notSquelched() {
        val p = FFTProcessor(fftSize, sampleRate)
        val ttnr = p.computeTTNR(toneOverFloorSpectrum())
        assertTrue(
            "first frame must already report the tone, got ${ttnr[200]}",
            ttnr[200] > 3.0
        )
    }

    /** [D3] A genuine energy jump (> ~6 dB in one frame interval) still squelches. */
    @Test
    fun d3_suddenEnergyJump_stillSquelched() {
        val p = FFTProcessor(fftSize, sampleRate)
        repeat(3) { p.computeTTNR(DoubleArray(fftSize / 2) { -80.0 }) }
        // +30 dB across the whole spectrum in one frame = a shock.
        val ttnr = p.computeTTNR(toneOverFloorSpectrum().map { it + 20.0 }.toDoubleArray())
        assertTrue("shock frame must report no emergence, got max ${ttnr.max()}", ttnr.max() <= 0.0)
    }

    @Test
    fun steadyTone_detectedAfterWarmup() {
        val p = FFTProcessor(fftSize, sampleRate)
        val spectrum = toneOverFloorSpectrum()
        var last = DoubleArray(0)
        repeat(12) { last = p.computeTTNR(spectrum) }
        assertTrue(
            "tone at bin 200 should exceed 10 dB TTNR after warmup, got ${last[200]}",
            last[200] > 10.0
        )
    }

    @Test
    fun noiseOnlyFloor_reportsNoEmergence() {
        val p = FFTProcessor(fftSize, sampleRate)
        val flat = DoubleArray(fftSize / 2) { -80.0 }
        var last = DoubleArray(0)
        repeat(5) { last = p.computeTTNR(flat) }
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
        val mags = FFTProcessor(fftSize, sampleRate).processFFT(input)

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
