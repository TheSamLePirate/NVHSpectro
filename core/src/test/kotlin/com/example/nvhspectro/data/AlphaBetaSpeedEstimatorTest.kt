package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [G1–G4, plan 2.4] The speed estimator that replaces the 1.2 s delay + raw derivative. */
class AlphaBetaSpeedEstimatorTest {

    private fun nanos(sec: Double) = (sec * 1e9).toLong()

    @Test
    fun g4_constantSpeed_convergesWithZeroAcceleration() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..10) e.update(nanos(i.toDouble()), 10f)
        assertEquals(10f, e.speedMps, 0.01f)
        assertEquals(0f, e.accelMps2, 0.05f)
    }

    @Test
    fun g4_linearRamp_tracksSpeedAndEstimatesAcceleration() {
        val e = AlphaBetaSpeedEstimator()
        // 1 m/s² ramp sampled at 1 Hz: v = t
        for (i in 0..20) e.update(nanos(i.toDouble()), i.toFloat())
        assertEquals(20f, e.speedMps, 0.5f)
        assertEquals(1f, e.accelMps2, 0.3f)
    }

    @Test
    fun l5_predictionBetweenFixes_extrapolatesLinearly() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..20) e.update(nanos(i.toDouble()), i.toFloat()) // a ≈ 1 m/s²
        val atHalf = e.predictAt(nanos(20.5))
        // ≈ v(20) + a·0.5 — the live H1 projection no longer needs to wait for the next fix
        assertEquals(e.speedMps + e.accelMps2 * 0.5f, atHalf, 1e-4f)
        assertTrue(atHalf > e.speedMps)
    }

    @Test
    fun g4_singleOutlier_isCoastedOver() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..9) e.update(nanos(i.toDouble()), 10f)
        e.update(nanos(10.0), 45f) // implies 35 m/s² — impossible
        assertEquals(10f, e.speedMps, 0.5f)
        e.update(nanos(11.0), 10f) // back to normal
        assertEquals(10f, e.speedMps, 0.5f)
    }

    @Test
    fun g4_twoConsecutiveImplausibleFixes_areAcceptedAsRealChange() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..9) e.update(nanos(i.toDouble()), 10f)
        e.update(nanos(10.0), 45f)
        e.update(nanos(11.0), 45f)
        assertTrue("second consistent fix must pull the estimate", e.speedMps > 20f)
    }

    @Test
    fun g1_nonMonotonicTimestamps_areIgnored() {
        val e = AlphaBetaSpeedEstimator()
        e.update(nanos(1.0), 10f)
        e.update(nanos(2.0), 10f)
        val before = e.speedMps
        e.update(nanos(1.5), 99f) // clock going backwards must not corrupt the state
        assertEquals(before, e.speedMps, 1e-6f)
    }

    @Test
    fun g4_longDropout_reseedsInsteadOfBlending() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(nanos(i.toDouble()), 20f)
        e.update(nanos(60.0), 3f) // 54 s gap: tunnel exit at a new speed
        assertEquals(3f, e.speedMps, 1e-4f)
        assertEquals(0f, e.accelMps2, 1e-4f)
    }

    @Test
    fun predictAt_capsExtrapolationHorizon() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..20) e.update(nanos(i.toDouble()), i.toFloat())
        val far = e.predictAt(nanos(60.0)) // 40 s later: capped at 2 s ahead
        assertEquals(e.speedMps + e.accelMps2 * 2f, far, 1e-3f)
    }

    @Test
    fun nanAndNegative_inputsAreSafe() {
        val e = AlphaBetaSpeedEstimator()
        e.update(nanos(0.0), Float.NaN)
        assertTrue(!e.hasFix)
        e.update(nanos(1.0), -5f) // negative Doppler clamps to 0
        assertEquals(0f, e.speedMps, 1e-6f)
    }

    @Test
    fun reset_returnsToColdStart() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(nanos(i.toDouble()), 15f)
        e.reset()
        assertTrue(!e.hasFix)
        assertEquals(0f, e.predictAt(nanos(10.0)), 1e-6f)
    }
}
