package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * [plan-gps GPS-2, §Tests GPS-2] The uncertainty-aware Kalman estimator.
 * gps02/gps04/gps06 are the FIXED-behavior replacements of the pins that
 * froze the α-β defects (σ ignored, no covariance, blind second outlier).
 */
class KalmanSpeedEstimatorTest {
    private fun nanos(sec: Double) = (sec * 1e9).toLong()

    private fun sample(
        sec: Double,
        mps: Float,
        sigma: Float? = 0.5f,
    ) = GnssSpeedSample(
        fixTimeNanos = nanos(sec),
        callbackTimeNanos = nanos(sec) + 20_000_000L,
        speedMps = mps,
        speedSigmaMps = sigma,
        source = SpeedSampleSource.GPS,
    )

    /** Constant-speed warmup at [rateHz]; the LAST fix lands exactly at t = [seconds]. */
    private fun converged(
        speed: Float,
        seconds: Int = 15,
        rateHz: Int = 1,
        sigma: Float? = 0.5f,
    ): KalmanSpeedEstimator {
        val e = KalmanSpeedEstimator()
        for (i in 0..seconds * rateHz) {
            assertNull(e.update(sample(i.toDouble() / rateHz, speed, sigma)))
        }
        return e
    }

    // ------------------------------------------------- GPS-2.1 core behavior

    @Test
    fun gps2_constantSpeed_convergesAtOneFiveAndTenHz() {
        for (rate in intArrayOf(1, 5, 10)) {
            val e = converged(20f, seconds = 15, rateHz = rate)
            val est = e.estimateAt(nanos(15.0))
            assertEquals("rate $rate Hz", 20f, est.speedMps, 0.05f)
            assertEquals("rate $rate Hz", 0f, est.accelerationMps2, 0.1f)
            // Filtering beats a single measurement: σv below the sensor's 0.5.
            assertTrue("rate $rate Hz", est.speedSigmaMps!! < 0.5f)
        }
    }

    @Test
    fun gps2_ramp_tracksSpeedAndEstimatesAcceleration() {
        // v = t (1 m/s² ramp) sampled at 1 Hz with exact measurements.
        val e = KalmanSpeedEstimator()
        for (i in 0..20) e.update(sample(i.toDouble(), i.toFloat()))
        val est = e.estimateAt(nanos(20.0))
        assertEquals(20f, est.speedMps, 0.4f)
        assertEquals(1f, est.accelerationMps2, 0.3f)
    }

    @Test
    fun gps2_hardBraking_isFollowedViaCoherentReacquisition() {
        // 20 m/s cruise, then −6 m/s² to a stop. The braking ONSET is
        // statistically implausible for a single 1 Hz fix (that is the NIS
        // gate doing its job), but the mutually-coherent pair of "rejected"
        // fixes reacquires on the very next fix — the maneuver is followed
        // with at most one fix of delay, never lost.
        val e = converged(20f, seconds = 10)
        var t = 10.0
        var truth = 20.0
        var rejections = 0
        while (truth > 0.0) {
            t += 1.0
            truth = (truth - 6.0).coerceAtLeast(0.0)
            if (e.update(sample(t, truth.toFloat())) != null) rejections++
        }
        assertTrue("at most the onset fix may be rejected (got $rejections)", rejections <= 1)
        val est = e.estimateAt(nanos(t))
        assertTrue("stopped or nearly (got ${est.speedMps})", est.speedMps < 2f)
    }

    @Test
    fun gps02_preciseFix_correctsMoreThanAnImpreciseOne() {
        // FIXED (was pinned_gps02): R = σv² weights the update [GPS-02].
        // A +2 m/s innovation that passes the NIS gate in both cases: the
        // precise fix must correct nearly fully, the σ=5 one barely at all.
        val precise = converged(10f, seconds = 6)
        val imprecise = converged(10f, seconds = 6)
        assertNull(precise.update(sample(7.0, 12f, sigma = 0.05f)))
        assertNull(imprecise.update(sample(7.0, 12f, sigma = 5f)))
        val vPrecise = precise.estimateAt(nanos(7.0)).speedMps
        val vImprecise = imprecise.estimateAt(nanos(7.0)).speedMps
        assertTrue(
            "precise σ must pull harder ($vPrecise vs $vImprecise)",
            vPrecise > vImprecise + 1f,
        )
        assertEquals(12f, vPrecise, 0.5f)
    }

    @Test
    fun gps04_estimateCarriesCovariance_thatGrowsDuringLoss() {
        // FIXED (was pinned_gps04): real σv/σa, growing with prediction age.
        val e = converged(15f, seconds = 10)
        val fresh = e.estimateAt(nanos(10.1))
        val stale = e.estimateAt(nanos(11.6))
        assertNotNull(fresh.speedSigmaMps)
        assertNotNull(fresh.accelerationSigmaMps2)
        assertTrue(
            "uncertainty must grow while coasting (${fresh.speedSigmaMps} → ${stale.speedSigmaMps})",
            stale.speedSigmaMps!! > fresh.speedSigmaMps!!,
        )
    }

    @Test
    fun gps2_validityFollowsPredictedSigma_onAHealthyStream() {
        val e = converged(15f, seconds = 10)
        assertEquals(EstimateValidity.VALID, e.estimateAt(nanos(10.2)).validity)
        assertEquals(EstimateValidity.PREDICTED, e.estimateAt(nanos(11.0)).validity)
        // By 1.6 s the predicted σv passes the DEGRADED limit; the hard
        // horizon turns it INVALID at 2 s.
        assertEquals(EstimateValidity.DEGRADED, e.estimateAt(nanos(11.7)).validity)
        assertEquals(EstimateValidity.INVALID, e.estimateAt(nanos(12.5)).validity)
    }

    // ------------------------------------------- GPS-2.2 rejection/reacquire

    @Test
    fun gps06_secondIncoherentOutlier_isAlsoRejected() {
        // FIXED (was pinned_gps06): 45 then 28 imply 17 m/s² between them —
        // NOT mutually coherent → both rejected, state stays at 10 m/s.
        val e = converged(10f, seconds = 10)
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(11.0, 45f)))
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(12.0, 28f)))
        assertEquals(10f, e.estimateAt(nanos(12.0)).speedMps, 1f)
        assertTrue("rejection NIS recorded", e.lastNis!! > 9.0)
    }

    @Test
    fun gps2_coherentOutlierPair_isAcceptedAsARealStepChange() {
        val e = converged(10f, seconds = 10)
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(11.0, 45f)))
        // 44.8 is coherent with the 45 candidate (0.2 m/s² implied) → real.
        assertNull(e.update(sample(12.0, 44.8f)))
        assertEquals(44.8f, e.estimateAt(nanos(12.0)).speedMps, 0.5f)
    }

    @Test
    fun gps2_rejectionStreak_forcesReacquisitionValve() {
        // Wild mutually-incoherent values must not lock the filter out forever.
        val e = converged(10f, seconds = 10)
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(11.0, 60f)))
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(12.0, 20f)))
        assertEquals(SampleRejection.OUTLIER_REJECTED, e.update(sample(13.0, 55f)))
        // 4th consecutive rejection trips the valve: forced re-seed, accepted.
        assertNull(e.update(sample(14.0, 18f)))
        assertEquals(18f, e.estimateAt(nanos(14.0)).speedMps, 0.5f)
    }

    @Test
    fun gps2_longLoss_isInvalidThenReseedsCleanly() {
        val e = converged(20f, seconds = 10)
        assertEquals(EstimateValidity.INVALID, e.estimateAt(nanos(70.0)).validity)
        // Tunnel exit at a very different speed: gap > reseed threshold.
        assertNull(e.update(sample(70.0, 3f)))
        val est = e.estimateAt(nanos(70.05))
        assertEquals(3f, est.speedMps, 0.2f)
        assertEquals(EstimateValidity.VALID, est.validity)
    }

    // --------------------------------------------- GPS-2.3 stationary state

    @Test
    fun gps2_stopAndGo_publishesHonestZeroWithHysteresis() {
        val e = KalmanSpeedEstimator()
        // Creep noise around a standstill: published speed is exactly 0.
        val creep = floatArrayOf(0.1f, 0.05f, 0.15f, 0.2f, 0.1f, 0.05f)
        for ((i, z) in creep.withIndex()) e.update(sample(i.toDouble(), z))
        assertEquals(0f, e.estimateAt(nanos(5.0)).speedMps, 0f)
        assertEquals(0f, e.estimateAt(nanos(5.0)).accelerationMps2, 0f)
        // σ stays truthful while stationary — never zeroed.
        assertTrue(e.estimateAt(nanos(5.0)).speedSigmaMps!! > 0f)
        // Pull-away: state must escape the stationary latch above the exit gate.
        e.update(sample(6.0, 0.9f))
        e.update(sample(7.0, 1.5f))
        e.update(sample(8.0, 2.2f))
        assertTrue(e.estimateAt(nanos(8.0)).speedMps > 1f)
    }

    // --------------------------------------------------- robustness matrix

    @Test
    fun gps2_irregularIntervals_stayFiniteEverywhere() {
        val e = KalmanSpeedEstimator()
        val dts = doubleArrayOf(0.2, 1.7, 0.05, 1.0, 3.0, 0.5, 0.11, 2.4, 0.9)
        var t = 0.0
        var truth = 5.0
        for (dt in dts) {
            t += dt
            truth += 0.5 * dt
            e.update(sample(t, truth.toFloat()))
            val est = e.estimateAt(nanos(t + 0.1))
            assertTrue(est.speedMps.isFinite())
            assertTrue(est.accelerationMps2.isFinite())
            assertTrue(est.speedSigmaMps!!.isFinite() && est.speedSigmaMps!! >= 0f)
            assertTrue(est.accelerationSigmaMps2!!.isFinite() && est.accelerationSigmaMps2!! >= 0f)
        }
        assertEquals(truth.toFloat(), e.estimateAt(nanos(t)).speedMps, 1f)
    }

    @Test
    fun gps2_sigmaExtremes_fromFiveCentimetersToFiveMeters_stayStable() {
        val e = KalmanSpeedEstimator()
        val rng = Random(42)
        for (i in 0..60) {
            val sigma = if (i % 2 == 0) 0.05f else 5f
            val noise = (rng.nextFloat() - 0.5f) * if (i % 2 == 0) 0.1f else 2f
            e.update(sample(i.toDouble(), 15f + noise, sigma))
        }
        val est = e.estimateAt(nanos(60.0))
        assertEquals(15f, est.speedMps, 0.5f)
        assertTrue(est.speedSigmaMps!!.isFinite())
    }

    @Test
    fun gps2_gate_lessNoiseThanRawSpeed_withNoAddedDelay() {
        // Gate GPS-2: the filtered track must beat raw Location.speed on noise
        // without trading it for lag.
        val rng = Random(7)
        val e = KalmanSpeedEstimator()
        var rawSse = 0.0
        var filteredSse = 0.0
        var n = 0
        for (i in 0..500) {
            val truth = 15.0
            val z = truth + (rng.nextDouble() - 0.5) * 2.0 * 0.8 // ±0.8 m/s noise
            e.update(sample(i.toDouble(), z.toFloat()))
            if (i >= 20) { // after convergence
                rawSse += (z - truth) * (z - truth)
                val v = e.estimateAt(nanos(i.toDouble())).speedMps.toDouble()
                filteredSse += (v - truth) * (v - truth)
                n++
            }
        }
        val rawRmse = sqrt(rawSse / n)
        val filteredRmse = sqrt(filteredSse / n)
        // The default q keeps the filter deliberately responsive (dynamics
        // matter more than smoothing), so the win is real but modest.
        assertTrue(
            "filtered RMSE $filteredRmse must beat raw $rawRmse",
            filteredRmse < rawRmse * 0.97,
        )
        // No delay: on an exact ramp the estimate at each fix instant stays
        // within a fraction of one fix interval of the truth.
        val ramp = KalmanSpeedEstimator()
        for (i in 0..15) {
            ramp.update(sample(i.toDouble(), i.toFloat()))
            if (i >= 8) {
                assertEquals(
                    "lag at t=$i",
                    i.toFloat(),
                    ramp.estimateAt(nanos(i.toDouble())).speedMps,
                    0.4f,
                )
            }
        }
    }

    @Test
    fun gps2_reset_returnsToInvalidColdStart() {
        val e = converged(12f, seconds = 5)
        e.reset()
        val est = e.estimateAt(nanos(6.0))
        assertEquals(EstimateValidity.INVALID, est.validity)
        assertNull(est.lastFixTimeNanos)
        assertNull(e.lastNis)
        assertTrue(abs(est.speedMps) == 0f)
    }
}
