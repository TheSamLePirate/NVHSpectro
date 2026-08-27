package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [plan-gps GPS-1.1, GPS-1.3] The session gate that fixes GPS-01/08/09 and
 * qualifies fixes (GPS-12/13). Test names follow the plan's §Tests GPS-1.
 */
class GnssSpeedSessionTest {
    private fun nanos(sec: Double) = (sec * 1e9).toLong()

    private fun sample(
        sec: Double,
        mps: Float,
        sigma: Float? = 0.5f,
        isMock: Boolean = false,
        deliveryDelaySec: Double = 0.05,
    ) = GnssSpeedSample(
        fixTimeNanos = nanos(sec),
        callbackTimeNanos = nanos(sec + deliveryDelaySec),
        speedMps = mps,
        speedSigmaMps = sigma,
        source = SpeedSampleSource.GPS,
        isMock = isMock,
    )

    private fun sessionAt20kmh(session: GnssSpeedSession = GnssSpeedSession()): GnssSpeedSession {
        for (i in 0..5) assertNull(session.update(sample(i.toDouble(), 5.56f)))
        return session
    }

    @Test
    fun gps01_staleEstimate_becomesInvalidAfterConfiguredHorizon() {
        val s = sessionAt20kmh()
        assertEquals(EstimateValidity.VALID, s.estimateAt(nanos(5.1)).validity)
        assertEquals(EstimateValidity.PREDICTED, s.estimateAt(nanos(6.5)).validity)
        assertEquals(EstimateValidity.INVALID, s.estimateAt(nanos(65.0)).validity)
    }

    @Test
    fun gps01_staleEstimate_neverDrivesOrderTracking() {
        val s = sessionAt20kmh()
        assertNotNull("within horizon the speed is usable", s.kinematicSpeedMps(nanos(6.5)))
        // Beyond the horizon: null, never the frozen v + a·cap number.
        assertNull(s.kinematicSpeedMps(nanos(65.0)))
    }

    @Test
    fun gps08_liveModeRestart_requiresFreshFix() {
        val s = sessionAt20kmh()
        // LIVE exit + re-entry — SpeedProvider.stop()/start() call reset().
        s.reset()
        assertEquals(EstimateValidity.INVALID, s.estimateAt(nanos(6.1)).validity)
        assertNull("no speed before the first fresh fix", s.kinematicSpeedMps(nanos(6.1)))
        assertNull(s.update(sample(7.0, 3f)))
        assertEquals(3f, s.kinematicSpeedMps(nanos(7.1))!!, 1e-4f)
    }

    @Test
    fun gps09_invalidEstimate_doesNotProduceRpmOrH1() {
        val s = GnssSpeedSession()
        // Cold start and stale state both refuse the kinematic chain.
        assertNull(s.kinematicSpeedMps(nanos(1.0)))
        sessionAt20kmh(s)
        assertNull(s.kinematicSpeedMps(nanos(120.0)))
    }

    @Test
    fun gps09_degradedEstimate_staysUsableUntilKalmanQuantifiesIt() {
        // Explicit GPS-1.1 rule: DEGRADED (no σv — every pre-API-26 device)
        // still drives kinematics; the LED shows the degradation. GPS-2
        // replaces this with a covariance-based decision.
        val s = GnssSpeedSession()
        for (i in 0..3) assertNull(s.update(sample(i.toDouble(), 10f, sigma = null)))
        assertEquals(EstimateValidity.DEGRADED, s.estimateAt(nanos(3.2)).validity)
        assertNotNull(s.kinematicSpeedMps(nanos(3.2)))
    }

    @Test
    fun gps12_mockFix_isFlaggedAndExcludedFromMeasurementMode() {
        val s = GnssSpeedSession()
        assertEquals(SampleRejection.MOCK_FIX, s.update(sample(0.0, 10f, isMock = true)))
        assertNull("mock fix must not have seeded the estimator", s.kinematicSpeedMps(nanos(0.1)))
        // A test build may explicitly allow mock fixes.
        val permissive = GnssSpeedSession(GnssSpeedSession.Config(acceptMockFixes = true))
        assertNull(permissive.update(sample(0.0, 10f, isMock = true)))
        assertNotNull(permissive.kinematicSpeedMps(nanos(0.1)))
    }

    @Test
    fun gps13_cachedFix_deliveredLate_isRejectedAsStale() {
        val s = GnssSpeedSession()
        // Classic case: the provider's first callback replays a minutes-old fix.
        assertEquals(
            SampleRejection.STALE_FIX,
            s.update(sample(0.0, 10f, deliveryDelaySec = 120.0)),
        )
        assertNull(s.kinematicSpeedMps(nanos(120.1)))
    }

    @Test
    fun gps13_nonFiniteAndNegativeSpeeds_areRejectedBeforeTheEstimator() {
        val s = GnssSpeedSession()
        assertEquals(SampleRejection.NON_FINITE_SPEED, s.update(sample(0.0, Float.NaN)))
        assertEquals(SampleRejection.NON_FINITE_SPEED, s.update(sample(0.0, Float.NEGATIVE_INFINITY)))
        assertEquals(SampleRejection.NEGATIVE_SPEED, s.update(sample(0.0, -1f)))
        assertNull(s.kinematicSpeedMps(nanos(0.1)))
    }
}
