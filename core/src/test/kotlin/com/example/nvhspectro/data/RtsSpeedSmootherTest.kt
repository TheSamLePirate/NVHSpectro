package com.example.nvhspectro.data

import com.example.nvhspectro.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * [plan-gps GPS-4.4] Deferred RTS reconstruction, including the plan's
 * replay comparison: raw Android speed vs the causal LIVE filter vs the
 * forward-backward smoother.
 */
class RtsSpeedSmootherTest {
    private fun nanos(sec: Double) = (sec * 1e9).toLong()

    private fun sample(
        sec: Double,
        mps: Double,
        sigma: Float? = 0.5f,
    ) = GnssSpeedSample(
        fixTimeNanos = nanos(sec),
        callbackTimeNanos = nanos(sec),
        speedMps = mps.toFloat(),
        speedSigmaMps = sigma,
        source = SpeedSampleSource.GPS,
    )

    @Test
    fun gps44_replayComparison_smoothedBeatsCausalBeatsRaw() {
        // Constant 15 m/s with deterministic ±0.8 m/s noise, 300 fixes at 1 Hz.
        val rng = Random(11)
        val truth = 15.0
        val fixes = (0..300).map { sample(it.toDouble(), truth + (rng.nextDouble() - 0.5) * 1.6) }

        val causal = KalmanSpeedEstimator()
        var rawSse = 0.0
        var causalSse = 0.0
        var n = 0
        for ((i, f) in fixes.withIndex()) {
            causal.update(f)
            if (i >= 20) {
                rawSse += (f.speedMps - truth) * (f.speedMps - truth)
                val v = causal.estimateAt(f.fixTimeNanos).speedMps - truth
                causalSse += v * v
                n++
            }
        }
        val knots = RtsSpeedSmoother.smooth(fixes)
        var smoothedSse = 0.0
        for (k in knots.drop(20)) smoothedSse += (k.speedMps - truth) * (k.speedMps - truth)

        val rawRmse = sqrt(rawSse / n)
        val causalRmse = sqrt(causalSse / n)
        val smoothedRmse = sqrt(smoothedSse / knots.drop(20).size)
        assertTrue("causal ($causalRmse) must beat raw ($rawRmse)", causalRmse < rawRmse)
        assertTrue("smoothed ($smoothedRmse) must beat causal ($causalRmse)", smoothedRmse < causalRmse)
    }

    @Test
    fun gps44_rampOnset_smoothedUsesTheFutureTheCausalFilterCannot() {
        // Cruise then a 1 m/s² ramp: at the ramp onset the causal filter lags,
        // the smoother sees the future fixes and does not.
        val fixes =
            (0..30).map { t ->
                val truth = if (t <= 10) 10.0 else 10.0 + (t - 10)
                sample(t.toDouble(), truth)
            }
        val causal = KalmanSpeedEstimator()
        var causalErrAtOnset = 0.0
        for ((i, f) in fixes.withIndex()) {
            causal.update(f)
            if (i in 11..14) {
                val truth = 10.0 + (i - 10)
                causalErrAtOnset += abs(causal.estimateAt(f.fixTimeNanos).speedMps - truth)
            }
        }
        val knots = RtsSpeedSmoother.smooth(fixes)
        var smoothedErrAtOnset = 0.0
        for (i in 11..14) smoothedErrAtOnset += abs(knots[i].speedMps - (10.0 + (i - 10)))
        assertTrue(
            "smoothed onset error ($smoothedErrAtOnset) < causal ($causalErrAtOnset)",
            smoothedErrAtOnset < causalErrAtOnset,
        )
    }

    @Test
    fun gps44_smoothedSigma_neverExceedsTheFilteredSigmaInside() {
        val rng = Random(3)
        val fixes = (0..60).map { sample(it.toDouble(), 12.0 + (rng.nextDouble() - 0.5)) }
        val knots = RtsSpeedSmoother.smooth(fixes)
        val causal = KalmanSpeedEstimator()
        for ((i, f) in fixes.withIndex()) {
            causal.update(f)
            if (i in 20..40) {
                val filteredSigma = causal.estimateAt(f.fixTimeNanos).speedSigmaMps!!.toDouble()
                assertTrue(
                    "σ_smoothed ≤ σ_filtered at $i",
                    knots[i].speedSigmaMps <= filteredSigma + 1e-6,
                )
            }
        }
        knots.forEach {
            assertTrue(it.speedMps.isFinite() && it.speedSigmaMps.isFinite() && it.speedSigmaMps >= 0.0)
        }
    }

    @Test
    fun gps44_longGap_splitsIntoIndependentSegments() {
        val before = (0..10).map { sample(it.toDouble(), 20.0) }
        val after = (0..10).map { sample(70.0 + it, 3.0) }
        val knots = RtsSpeedSmoother.smooth(before + after)
        assertEquals(before.size + after.size, knots.size)
        // No bleed across the 60 s hole in either direction.
        assertEquals(20.0, knots[before.size - 1].speedMps, 0.3)
        assertEquals(3.0, knots[before.size].speedMps, 0.3)
    }

    @Test
    fun gps44_outliers_areDroppedOffline() {
        val fixes =
            (0..20).map { sample(it.toDouble(), 10.0) } +
                sample(21.0, 45.0) + // multipath spike
                (22..40).map { sample(it.toDouble(), 10.0) }
        val knots = RtsSpeedSmoother.smooth(fixes)
        assertEquals("the spike produces no knot", fixes.size - 1, knots.size)
        knots.forEach { assertEquals(10.0, it.speedMps, 0.5) }
    }

    // ------------------------------------------------- reconstruction wiring

    @Test
    fun gps44_reconstruction_smoothsFrameRateSamplesSharingOneFixPerSecond() {
        // The recorder's shape: ~10 frames per 1 Hz fix, noisy raw speed.
        val rng = Random(7)
        val samples = mutableListOf<TelemetryData>()
        val audioTimes = mutableListOf<Long>()
        for (frame in 0 until 200) {
            val fixSec = frame / 10 // 10 frames share each fix
            val noisy = 15.0 + (rng.nextInt(3) - 1) * 0.4
            samples.add(
                TelemetryData(
                    speedKmh = (noisy * 3.6).toFloat(),
                    speedAccuracyMs = 0.5f,
                    elapsedRealtimeNanos = nanos(fixSec.toDouble()),
                ),
            )
            audioTimes.add(nanos(frame / 10.0))
        }
        val result = SpeedReconstruction.reconstruct(samples, audioTimes)
        assertEquals(SpeedReconstruction.STATUS_SMOOTHED, result.statusLabel)
        assertEquals(samples.size, result.telemetry.size)
        val mid = result.telemetry[100]
        assertEquals(15.0 * 3.6, mid.theoreticalSpeedKmh.toDouble(), 2.0)
        assertTrue(mid.theoreticalSpeedSigmaKmh!! > 0f)
        assertEquals(EstimateValidity.VALID, mid.speedValidity)
    }

    @Test
    fun gps44_sidecarWithoutTimestamps_fallsBackToInterpolationAndSaysSo() {
        // v1-shaped: no monotonic stamps at all.
        val samples = List(30) { TelemetryData(speedKmh = 20f) }
        val result = SpeedReconstruction.reconstruct(samples, null)
        assertEquals(SpeedReconstruction.STATUS_INTERPOLATED, result.statusLabel)
        assertEquals(samples.size, result.telemetry.size)
        assertNull(result.telemetry[0].theoreticalSpeedSigmaKmh)
    }
}
