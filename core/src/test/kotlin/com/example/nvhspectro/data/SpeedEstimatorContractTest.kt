package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan-gps GPS-0.2, GPS-0.3] The [SpeedEstimator] contract on the α-β
 * implementation, plus pinned characterizations of the defects the GPS plan
 * fixes (GPS-01/02/04/06/08). Pinned tests freeze CURRENT (defective)
 * behavior — update each in the same commit as its fix, never delete one to
 * make a build pass.
 */
class SpeedEstimatorContractTest {
    private fun nanos(sec: Double) = (sec * 1e9).toLong()

    private fun sample(
        sec: Double,
        mps: Float,
        sigma: Float? = 0.5f,
        source: SpeedSampleSource = SpeedSampleSource.GPS,
    ) = GnssSpeedSample(
        fixTimeNanos = nanos(sec),
        callbackTimeNanos = nanos(sec) + 50_000_000L,
        speedMps = mps,
        speedSigmaMps = sigma,
        source = source,
    )

    // ------------------------------------------------------- contract (GPS-0.2)

    @Test
    fun gps0_sampleUpdate_matchesLegacyNumericBehavior() {
        val legacy = AlphaBetaSpeedEstimator()
        val viaSample = AlphaBetaSpeedEstimator()
        for (i in 0..20) {
            legacy.update(nanos(i.toDouble()), i.toFloat())
            viaSample.update(sample(i.toDouble(), i.toFloat()))
        }
        assertEquals(legacy.speedMps, viaSample.speedMps, 1e-6f)
        assertEquals(legacy.accelMps2, viaSample.accelMps2, 1e-6f)
        assertEquals(legacy.predictAt(nanos(20.5)), viaSample.predictAt(nanos(20.5)), 1e-6f)
        // Gate GPS-0: introducing the contract changed no LIVE output.
        assertEquals(
            legacy.predictAt(nanos(20.5)),
            viaSample.estimateAt(nanos(20.5)).speedMps,
            1e-6f,
        )
    }

    @Test
    fun gps0_beforeAnyFix_estimateIsInvalidWithNullAbsences() {
        val est = AlphaBetaSpeedEstimator().estimateAt(nanos(5.0))
        assertEquals(EstimateValidity.INVALID, est.validity)
        assertNull("no fix → null, not a 0/NaN sentinel", est.lastFixTimeNanos)
        assertNull(est.ageSinceFixNanos)
        assertEquals(0f, est.speedMps, 0f)
    }

    @Test
    fun gps0_estimate_isValidAtFixThenPredictedBetweenFixes() {
        val e = AlphaBetaSpeedEstimator()
        e.update(sample(0.0, 10f))
        e.update(sample(1.0, 10f))
        assertEquals(EstimateValidity.VALID, e.estimateAt(nanos(1.1)).validity)
        val between = e.estimateAt(nanos(1.8))
        assertEquals(EstimateValidity.PREDICTED, between.validity)
        assertEquals(nanos(0.8), between.ageSinceFixNanos)
        assertEquals(nanos(1.0), between.lastFixTimeNanos)
    }

    @Test
    fun gps0_missingSigma_marksEstimateDegraded() {
        val e = AlphaBetaSpeedEstimator()
        e.update(sample(0.0, 10f, sigma = null))
        e.update(sample(1.0, 10f, sigma = null))
        assertEquals(EstimateValidity.DEGRADED, e.estimateAt(nanos(1.1)).validity)
    }

    @Test
    fun gps0_rejectionsAreReported() {
        val e = AlphaBetaSpeedEstimator()
        assertEquals(SampleRejection.NAN_SPEED, e.update(sample(0.0, Float.NaN)))
        assertNull("seed accepted", e.update(sample(1.0, 10f)))
        assertEquals(SampleRejection.NON_MONOTONIC_TIME, e.update(sample(0.5, 12f)))
        for (i in 2..9) assertNull(e.update(sample(i.toDouble(), 10f)))
        assertEquals(SampleRejection.OUTLIER_COASTED, e.update(sample(10.0, 45f)))
    }

    @Test
    fun gps0_reset_returnsToInvalidColdStart() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(sample(i.toDouble(), 15f))
        e.reset()
        val est = e.estimateAt(nanos(6.0))
        assertEquals(EstimateValidity.INVALID, est.validity)
        assertNull(est.lastFixTimeNanos)
        assertEquals(0f, est.speedMps, 0f)
    }

    // ------------------------------------- pinned current defects (GPS-0.3)

    @Test
    fun pinned_gps01_staleEstimate_keepsServingAFrozenSpeed() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(sample(i.toDouble(), 20f))
        // 60 s of GNSS loss (tunnel). The estimate DESCRIBES itself as INVALID…
        val est = e.estimateAt(nanos(65.0))
        assertEquals(EstimateValidity.INVALID, est.validity)
        // …but the numeric channel still carries the frozen v + a·2s, and
        // predictAt — the path SpeedProvider actually reads — returns it too.
        // GPS-1.1 makes INVALID interrupt the kinematic chain.
        assertEquals(20f, est.speedMps, 0.5f)
        assertEquals(20f, e.predictAt(nanos(65.0)), 0.5f)
    }

    @Test
    fun pinned_gps02_impreciseFix_movesTheEstimateExactlyLikeAPreciseOne() {
        val precise = AlphaBetaSpeedEstimator()
        val degraded = AlphaBetaSpeedEstimator()
        for (i in 0..5) {
            precise.update(sample(i.toDouble(), 10f, sigma = 0.05f))
            degraded.update(sample(i.toDouble(), 10f, sigma = 5f))
        }
        // Same +4 m/s residual, declared σv 100× apart → identical correction.
        // GPS-2's Kalman weights by R = σv².
        precise.update(sample(6.0, 14f, sigma = 0.05f))
        degraded.update(sample(6.0, 14f, sigma = 5f))
        assertEquals(precise.speedMps, degraded.speedMps, 1e-6f)
        assertEquals(precise.accelMps2, degraded.accelMps2, 1e-6f)
    }

    @Test
    fun pinned_gps04_estimateCarriesNoCovariance() {
        val e = AlphaBetaSpeedEstimator()
        e.update(sample(0.0, 10f))
        e.update(sample(1.0, 10f))
        val est = e.estimateAt(nanos(1.5))
        // The α-β has no covariance to report — honest nulls, never fake σ.
        assertNull(est.speedSigmaMps)
        assertNull(est.accelerationSigmaMps2)
    }

    @Test
    fun pinned_gps06_secondIncoherentOutlier_isAcceptedAsRealChange() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..9) e.update(sample(i.toDouble(), 10f))
        // First implausible fix (+35 m/s in 1 s) is coasted…
        assertEquals(SampleRejection.OUTLIER_COASTED, e.update(sample(10.0, 45f)))
        // …then a second implausible fix is accepted although it is NOT
        // coherent with the first (28 ≠ 45): two successive multipath errors
        // read as a real step change. GPS-2.2 adds the NIS coherence test.
        assertNull(e.update(sample(11.0, 28f)))
        assertTrue("estimate was pulled by the incoherent pair", e.speedMps > 15f)
    }

    @Test
    fun pinned_gps08_sessionRestartWithoutReset_reexposesTheOldSpeed() {
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(sample(i.toDouble(), 25f))
        // LIVE exit then re-entry: SpeedProvider.stop()/start() never calls
        // reset() today, so before the first new fix the old session's speed
        // is still served. GPS-1.1 requires a fresh fix after every re-entry.
        val atReentry = e.estimateAt(nanos(90.0))
        assertEquals(25f, atReentry.speedMps, 0.5f)
        assertTrue(e.hasFix)
    }
}
