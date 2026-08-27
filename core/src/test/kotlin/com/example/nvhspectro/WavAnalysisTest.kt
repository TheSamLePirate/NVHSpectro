package com.example.nvhspectro

import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.testutil.SynthSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan 3.3/3.6] The extracted analyzer computation core: STFT sweep,
 * theoretical-speed interpolation, playback-cursor state and the order sweep.
 */
class WavAnalysisTest {
    private val sampleRate = AudioConfig.LIVE_SAMPLE_RATE_HZ
    private val fftSize = AudioConfig.WAV_FFT_SIZE

    @Test
    fun computeSpectrogram_findsToneAtItsBin_withExpectedFrameCount() {
        val bin = 200
        val pcm = SynthSignals.sine(SynthSignals.binCenteredFreq(bin, fftSize, sampleRate), sampleRate, sampleRate) // 1 s
        val spectro = WavAnalysis.computeSpectrogram(pcm, sampleRate, fftSize)!!

        val expectedFrames = ((pcm.size - fftSize) / (fftSize / 2)).coerceAtLeast(1)
        assertEquals(expectedFrames, spectro.absList.size)
        assertEquals(expectedFrames, spectro.ttnrList.size)

        val lastFrame = spectro.absList.last()
        val peakBin = lastFrame.indices.maxByOrNull { lastFrame[it] }
        assertEquals("tone must sit on its own bin", bin, peakBin)
    }

    @Test
    fun computeSpectrogram_tooShortPcm_returnsNull() {
        assertNull(WavAnalysis.computeSpectrogram(ShortArray(fftSize - 1), sampleRate, fftSize))
    }

    @Test
    fun computeSpectrogram_checkActive_abortsTheSweep() {
        val pcm = ShortArray(sampleRate) // 1 s of silence
        var calls = 0
        try {
            WavAnalysis.computeSpectrogram(pcm, sampleRate, fftSize) {
                if (++calls >= 3) throw InterruptedException("cancelled")
            }
            throw AssertionError("sweep must stop when checkActive throws")
        } catch (e: InterruptedException) {
            assertEquals(3, calls)
        }
    }

    @Test
    fun interpolateTheoreticalSpeed_linearBetweenCorners() {
        val telemetry =
            listOf(
                TelemetryData(speedKmh = 10f),
                TelemetryData(speedKmh = 10f),
                TelemetryData(speedKmh = 10f),
                TelemetryData(speedKmh = 30f),
            )
        val out = WavAnalysis.interpolateTheoreticalSpeed(telemetry)
        // Corners at index 0 (10) and 3 (30): indices 1..2 interpolate linearly.
        assertEquals(10f, out[0].theoreticalSpeedKmh)
        assertTrue(out[1].theoreticalSpeedKmh in 16f..17.5f)
        assertTrue(out[2].theoreticalSpeedKmh in 23f..24f)
        assertEquals(30f, out[3].theoreticalSpeedKmh)
    }

    @Test
    fun interpolateTheoreticalSpeed_existingTheo_untouched() {
        val telemetry =
            listOf(
                TelemetryData(speedKmh = 10f, theoreticalSpeedKmh = 11f),
                TelemetryData(speedKmh = 20f, theoreticalSpeedKmh = 21f),
            )
        assertEquals(telemetry, WavAnalysis.interpolateTheoreticalSpeed(telemetry))
    }

    // --- Cursor state -----------------------------------------------------

    private val config = KinematicsConfig(isEnabled = true, v1000Kmh = 10.0, selectedTrackedOrder = 18.0)

    @Test
    fun c17_cursorStateAt_mapsFrameAndInterpolatesTelemetry() {
        val frames = List(100) { FloatArray(1024) }
        val telemetry = listOf(TelemetryData(speedKmh = 10f), TelemetryData(speedKmh = 20f))
        val cursor =
            WavAnalysis.cursorStateAt(
                posMs = 5000L,
                durationMs = 10_000L,
                spectrogram = WavAnalysis.Spectrogram(frames, frames, sampleRate),
                telemetrySource = telemetry,
                config = KinematicsConfig(),
            )
        // Halfway: frame index ~50 of 100, telemetry interpolated between the 2 samples.
        assertEquals(50, cursor.frameIndex)
        assertEquals(15f, cursor.telemetry.theoreticalSpeedKmh, 0.5f)
    }

    @Test
    fun cursorStateAt_readsTrackedOrderLevelsAroundProjectedBin() {
        val df = (sampleRate / 2.0) / 1024
        // 18 km/h at V1000=10 → H1 = 30 Hz → order 18 at 540 Hz.
        val targetBin = Math.round(18.0 * 30.0 / df).toInt()
        val abs = FloatArray(1024) { -90f }.also { it[targetBin] = -35f }
        val ttnr = FloatArray(1024).also { it[targetBin] = 8f }
        val telemetry = listOf(TelemetryData(speedKmh = 18f, theoreticalSpeedKmh = 18f))

        val cursor =
            WavAnalysis.cursorStateAt(
                posMs = 0L,
                durationMs = 1000L,
                spectrogram = WavAnalysis.Spectrogram(listOf(abs), listOf(ttnr), sampleRate),
                telemetrySource = telemetry,
                config = config,
            )
        assertEquals(-35.0, cursor.telemetry.trackedOrderDbFS, 1e-6)
        assertEquals(8.0, cursor.telemetry.trackedOrderEmergenceDb, 1e-6)
        assertEquals(8f, cursor.telemetry.ttnrDb)
        assertEquals(true, cursor.telemetry.trackedOrderIdentifiable)
    }

    @Test
    fun gps10_cursorWithUncertainSpeed_suspendsTheTrackedOrder() {
        // The audit case: σv = 1.8 km/h at V1000 = 10 and H18 → k·σf = 108 Hz,
        // far beyond the half-order bound → "non identifiable", never an
        // ambiguous level [GPS-4.2].
        val abs = FloatArray(1024) { -35f }
        val telemetry =
            listOf(
                TelemetryData(
                    speedKmh = 30f,
                    theoreticalSpeedKmh = 30f,
                    theoreticalSpeedSigmaKmh = 1.8f,
                ),
            )
        val cursor =
            WavAnalysis.cursorStateAt(
                posMs = 0L,
                durationMs = 1000L,
                spectrogram = WavAnalysis.Spectrogram(listOf(abs), listOf(FloatArray(1024)), sampleRate),
                telemetrySource = telemetry,
                config = config,
            )
        assertEquals(false, cursor.telemetry.trackedOrderIdentifiable)
        assertEquals(-120.0, cursor.telemetry.trackedOrderDbFS, 1e-6)
    }

    // --- Order sweep ------------------------------------------------------

    @Test
    fun a2_orderSweep_tagsSteadyOrderAndFillsTelemetryLevels() {
        val binCount = 1024
        val df = (sampleRate / 2.0) / binCount
        val order18Bin = (18.0 * 30.0 / df).toInt()
        val ttnrRow = FloatArray(binCount).also { it[order18Bin] = 10f }
        val absRow =
            FloatArray(binCount) { -90f }.also {
                for (b in order18Bin - 3..order18Bin + 3) it[b] = -40f
            }
        val frames = 60
        val absHistory = List(frames) { absRow }
        val ttnrHistory = List(frames) { ttnrRow }
        val telemetry = List(4) { TelemetryData(speedKmh = 18f, theoreticalSpeedKmh = 18f) }

        val sweep = WavAnalysis.orderSweep(absHistory, ttnrHistory, telemetry, config, sampleRate)

        assertEquals("every telemetry sample gets tracked-order levels", -40.0, sweep.updatedTelemetry[0].trackedOrderDbFS, 1e-6)
        assertEquals(frames, sweep.tagsByFrame.size)
        assertTrue("EMA-converged frames must carry the tag", sweep.tagsByFrame[frames - 1]!!.isNotEmpty())
        assertEquals("one steady order = one report row", 1, sweep.report.size)
        assertEquals(17.9, sweep.report[0].orderValue, 1e-9)
    }

    @Test
    fun a2_orderSweep_stoppedVehicle_producesNothing() {
        val frames = 20
        val row = FloatArray(64)
        val sweep =
            WavAnalysis.orderSweep(
                List(frames) { row },
                List(frames) { row },
                List(4) { TelemetryData(speedKmh = 0f) },
                config,
                sampleRate,
            )
        assertTrue(sweep.report.isEmpty())
        assertTrue(sweep.tagsByFrame.values.all { it.isEmpty() })
    }
}
