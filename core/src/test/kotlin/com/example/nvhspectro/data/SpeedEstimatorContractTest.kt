package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan-gps GPS-0.2, GPS-0.3] The [SpeedEstimator] contract on the α-β
 * implementation. Every GPS-0 pin is now closed: GPS-01/GPS-08 by GPS-1.1
 * (gps01_* here, gps08_* in GnssSpeedSessionTest) and GPS-02/04/06 by the
 * GPS-2 Kalman (gps02/gps04/gps06 tests in KalmanSpeedEstimatorTest — the
 * production estimator behind GnssSpeedSession). The α-β stays as the
 * fixed-gain A/B baseline for replay tuning; its σ-blindness is by design
 * and documented here, no longer a pinned app defect.
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
        assertEquals(SampleRejection.NON_FINITE_SPEED, e.update(sample(0.0, Float.NaN)))
        assertEquals(SampleRejection.NON_FINITE_SPEED, e.update(sample(0.0, Float.POSITIVE_INFINITY)))
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
    fun gps01_estimatorDescribesStaleStateAsInvalid() {
        // FIXED by GPS-1.1 (was pinned_gps01): a stale estimate is INVALID and
        // the kinematic chain gates on that validity, not on the diagnostic
        // number (which is retained for traces per GPS-D4). The session-level
        // enforcement tests live in GnssSpeedSessionTest.
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..5) e.update(sample(i.toDouble(), 20f))
        assertEquals(
            EstimateValidity.PREDICTED,
            e.estimateAt(nanos(5.0 + 1.9)).validity,
        )
        val stale = e.estimateAt(nanos(65.0)) // 60 s of GNSS loss (tunnel)
        assertEquals(EstimateValidity.INVALID, stale.validity)
    }

    @Test
    fun gps04_alphaBetaBaseline_reportsHonestNullCovariance() {
        // The A/B baseline has no covariance to report — honest nulls, never a
        // fake σ. The production path's real covariance is tested on the
        // Kalman (gps04_* in KalmanSpeedEstimatorTest).
        val e = AlphaBetaSpeedEstimator()
        e.update(sample(0.0, 10f))
        e.update(sample(1.0, 10f))
        val est = e.estimateAt(nanos(1.5))
        assertNull(est.speedSigmaMps)
        assertNull(est.accelerationSigmaMps2)
        assertNull(e.lastNis)
    }

    @Test
    fun alphaBetaBaseline_coastsOneOutlierThenAcceptsTheNextFix() {
        // Documented fixed-gain baseline behavior (no coherence test) — the
        // production NIS gate lives in the Kalman (gps06_* tests).
        val e = AlphaBetaSpeedEstimator()
        for (i in 0..9) e.update(sample(i.toDouble(), 10f))
        assertEquals(SampleRejection.OUTLIER_COASTED, e.update(sample(10.0, 45f)))
        assertNull(e.update(sample(11.0, 28f)))
        assertTrue(e.speedMps > 15f)
    }
}
