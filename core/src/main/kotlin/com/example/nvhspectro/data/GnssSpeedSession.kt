package com.example.nvhspectro.data

/**
 * [plan-gps GPS-1.1, GPS-1.3] The GNSS speed session: fix qualification,
 * validity enforcement and session lifecycle around a [SpeedEstimator].
 *
 * This is the single gate between Android's Location callbacks and the
 * kinematic chain (RPM / H1 / order tracking):
 *
 * - every sample is qualified BEFORE it can touch the estimator — non-finite
 *   or negative speeds, mock fixes (unless allowed by [Config.acceptMockFixes])
 *   and cached/backlogged fixes are rejected with a typed reason [GPS-12,
 *   GPS-13];
 * - [kinematicSpeedMps] is the ONLY speed the kinematic chain may consume
 *   [GPS-09]: it returns null — never a frozen number — once the estimate is
 *   INVALID (no fix, or beyond the prediction horizon) [GPS-01];
 * - [reset] starts a fresh session: callers MUST invoke it on every LIVE-mode
 *   entry/exit so no previous session's speed survives a restart [GPS-08].
 *
 * The estimate's numeric fields remain populated even when INVALID — they are
 * diagnostic values for traces and displays that show their own "--" state
 * [GPS-D4]; computation goes through [kinematicSpeedMps] only.
 *
 * Units and time bases [GPS-0.5]: m/s, m/s², BOOTTIME nanoseconds. Horizontal
 * position accuracy is deliberately absent here — it is never a mathematical
 * substitute for speed accuracy [GPS-1.3]; the σv-less case is classified
 * DEGRADED by the estimator instead.
 *
 * Pure Kotlin — no Android imports — JVM-unit-testable.
 */
class GnssSpeedSession(
    private val config: Config = Config(),
    // [GPS-2] The uncertainty-aware Kalman is the production estimator; the
    // α-β remains available for A/B comparison and replay tuning.
    private val estimator: SpeedEstimator =
        KalmanSpeedEstimator(
            KalmanSpeedEstimator.Config(
                predictionHorizonSeconds = config.predictionHorizonSeconds.toDouble(),
            ),
        ),
) : SpeedEstimator {
    /** Named, testable thresholds [plan-gps §2] — PROVISIONAL until Gate GPS-5 tuning. */
    data class Config(
        /** Beyond this age the estimate is INVALID, never a frozen speed [GPS-01]. */
        val predictionHorizonSeconds: Float = 2f,
        /** A fix delivered later than this after its own timestamp is cached/backlogged. */
        val maxDeliveryAgeNanos: Long = 2_000_000_000L,
        /** Measurement mode rejects mock fixes; test builds may allow them [GPS-12]. */
        val acceptMockFixes: Boolean = false,
    )

    override fun update(sample: GnssSpeedSample): SampleRejection? = qualify(sample) ?: estimator.update(sample)

    override fun estimateAt(elapsedRealtimeNanos: Long): SpeedEstimate = estimator.estimateAt(elapsedRealtimeNanos)

    override val lastNis: Double? get() = estimator.lastNis

    override val description: String get() = estimator.description

    override fun reset() = estimator.reset()

    /**
     * [GPS-09] The only entry point for RPM/H1/order math. Null = no usable
     * speed (INVALID) — callers must suspend kinematic tracking, not coast.
     */
    fun kinematicSpeedMps(elapsedRealtimeNanos: Long): Float? {
        val estimate = estimateAt(elapsedRealtimeNanos)
        return if (estimate.validity.usableForKinematics) estimate.speedMps else null
    }

    /** [GPS-1.3] Minimal fix qualification, applied before the estimator sees anything. */
    private fun qualify(sample: GnssSpeedSample): SampleRejection? =
        when {
            !sample.speedMps.isFinite() -> SampleRejection.NON_FINITE_SPEED
            sample.speedMps < 0f -> SampleRejection.NEGATIVE_SPEED
            sample.isMock && !config.acceptMockFixes -> SampleRejection.MOCK_FIX
            sample.callbackTimeNanos - sample.fixTimeNanos > config.maxDeliveryAgeNanos ->
                SampleRejection.STALE_FIX
            else -> null
        }
}
