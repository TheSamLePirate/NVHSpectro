/*
 * [plan-gps GPS-0.1] Pure contracts for the GNSS speed chain
 * (audit-gps GPS-01, GPS-04, GPS-09).
 *
 * Units and time bases [GPS-0.5]:
 * - every `*TimeNanos` / `*Nanos` field is Android BOOTTIME — the monotonic
 *   clock of `SystemClock.elapsedRealtimeNanos()` and
 *   `Location.getElapsedRealtimeNanos()`. UTC wall-clock time NEVER enters
 *   interval math [audit G1].
 * - speeds are m/s, accelerations m/s²; every `*Sigma*` is a 1-σ standard
 *   deviation of the quantity it names.
 * - absence is `null`, never a numeric sentinel: no 0-as-unknown, no NaN
 *   markers (Gate GPS-0).
 */
package com.example.nvhspectro.data

/** Which subscription produced a speed sample [GPS-07]. */
enum class SpeedSampleSource {
    /** Direct LocationManager GPS_PROVIDER subscription — the metrological path. */
    GPS,

    /** Fused-provider fallback fix whose `provider` field claims GNSS provenance. */
    FUSED_GNSS,
}

/**
 * Validity of a [SpeedEstimate] [GPS-01, GPS-09]. In GPS-0 this is DESCRIPTIVE
 * only — nothing enforces it yet; GPS-1.1 makes it gate every kinematic use.
 */
enum class EstimateValidity {
    /** Fresh: evaluated at (or within a fraction of a fix interval of) an accepted fix. */
    VALID,

    /** Model extrapolation between fixes, still inside the prediction horizon. */
    PREDICTED,

    /** Usable state but unqualified uncertainty (e.g. the fix carried no σv). */
    DEGRADED,

    /** No fix, or the last fix is beyond the prediction horizon — not a measurement. */
    INVALID,
}

/** Why a sample did not update the estimator state normally [GPS-06, GPS-13]. */
enum class SampleRejection {
    /** `Location.speed` was NaN. State untouched. */
    NAN_SPEED,

    /** Fix time not after the previous accepted fix [G1]. State untouched. */
    NON_MONOTONIC_TIME,

    /** Residual implies an implausible acceleration; coasted on the prediction [G4]. */
    OUTLIER_COASTED,
}

/**
 * One qualified GNSS speed measurement, as delivered by Android.
 * GPS-3 extends this with signal diagnostics (satellites, C/N0, constellations).
 */
data class GnssSpeedSample(
    /** BOOTTIME of the fix itself (`Location.getElapsedRealtimeNanos()`). */
    val fixTimeNanos: Long,
    /**
     * BOOTTIME when the callback delivered the fix to the app;
     * `callbackTimeNanos - fixTimeNanos` is the delivery latency [GPS-13].
     */
    val callbackTimeNanos: Long,
    /** Doppler speed reported by Android (`Location.speed`), m/s. */
    val speedMps: Float,
    /** 1-σ speed error, m/s; null when the device reports none — never 0-as-unknown. */
    val speedSigmaMps: Float? = null,
    val source: SpeedSampleSource,
    /** Mock-provider fix — must be excludable from measurement mode [GPS-12]. */
    val isMock: Boolean = false,
)

/** The estimator's answer at one instant — speed plus age, uncertainty and validity. */
data class SpeedEstimate(
    /** BOOTTIME this estimate was evaluated at. */
    val estimateTimeNanos: Long,
    /** BOOTTIME of the last accepted fix; null = no fix since reset. */
    val lastFixTimeNanos: Long?,
    val speedMps: Float,
    val accelerationMps2: Float,
    /** 1-σ of [speedMps]; null = the estimator carries no covariance (pinned GPS-04). */
    val speedSigmaMps: Float? = null,
    /** 1-σ of [accelerationMps2]; null = no covariance (pinned GPS-04). */
    val accelerationSigmaMps2: Float? = null,
    /** `estimateTimeNanos - lastFixTimeNanos` (≥ 0); null = no fix since reset. */
    val ageSinceFixNanos: Long?,
    val validity: EstimateValidity,
)

/**
 * [plan-gps GPS-0.2] The speed-estimation contract. GPS-2 swaps the α-β
 * implementation for a Kalman filter behind this same interface.
 */
interface SpeedEstimator {
    /**
     * Feed one qualified GNSS sample. Returns null when the sample was accepted
     * (including a re-seed after a long dropout), else why it was not.
     */
    fun update(sample: GnssSpeedSample): SampleRejection?

    /**
     * Evaluate the state at a BOOTTIME instant. GPS-1.2's target call is
     * `estimateAt(audioFrame.centerTimeNanos)` — never "estimate now" [GPS-03].
     */
    fun estimateAt(elapsedRealtimeNanos: Long): SpeedEstimate

    /** Cold start: forget fixes, state and validity (mode transitions — GPS-08). */
    fun reset()
}
