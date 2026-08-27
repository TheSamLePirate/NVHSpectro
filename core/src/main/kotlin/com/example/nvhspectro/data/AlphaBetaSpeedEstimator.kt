package com.example.nvhspectro.data

import kotlin.math.abs

/**
 * α-β tracker over GNSS Doppler speed [audit G1–G4, plan 2.4], now behind the
 * [SpeedEstimator] contract [plan-gps GPS-0.2].
 *
 * Purpose: turn ~1 Hz Doppler fixes into a continuously *predictable* speed
 * (v + a·dt) so the live order projection needs no lookahead — this is what
 * deletes the old 1.2 s display latency [L5]. Acceleration falls out of the
 * filter as a by-product, replacing the raw 1 Hz derivative whose wall-clock
 * dt made it spike on clock adjustments [G1].
 *
 * Units and time base [GPS-0.5]: speeds m/s, accelerations m/s²; every
 * timestamp is BOOTTIME nanoseconds (elapsedRealtimeNanos) — never UTC.
 * Pure Kotlin — no Android imports — so it is JVM-unit-testable.
 *
 * Gains: α=0.5 with β=α²/(2−α)≈0.17 (critically damped pairing for ~1 Hz
 * updates). PROVISIONAL until tuned on the Phase 0.8 field drive logs.
 *
 * Known limitations, deliberately pinned by `SpeedEstimatorContractTest`
 * until the GPS plan replaces them (audit-gps §4):
 * - beyond [maxPredictAheadSeconds] the numeric output freezes at v + a·cap;
 *   [estimateAt] *describes* that as INVALID but nothing enforces it [GPS-01];
 * - the measurement σv is recorded, not weighted — a ±5 m/s fix corrects the
 *   state exactly like a ±0.05 m/s one [GPS-02];
 * - no covariance: estimates carry null sigmas [GPS-04];
 * - a second consecutive implausible fix is accepted without testing its
 *   coherence with the first [GPS-06].
 */
class AlphaBetaSpeedEstimator(
    private val alpha: Float = 0.5f,
    private val beta: Float = 0.17f,
    /** Residual implying more than this acceleration is a single-fix outlier [G4]. */
    private val maxPlausibleAccelMps2: Float = 12f,
    /** No fix for longer than this → re-seed instead of blending [G4]. */
    private val reseedAfterSeconds: Float = 3f,
    /** Never extrapolate a prediction further than this beyond the last fix. */
    private val maxPredictAheadSeconds: Float = 2f,
) : SpeedEstimator {
    /** Filtered speed in m/s at the time of the last accepted update. */
    var speedMps: Float = 0f
        private set

    /** Filtered acceleration in m/s². */
    var accelMps2: Float = 0f
        private set

    private var lastUpdateNanos = Long.MIN_VALUE
    private var pendingOutlier = false

    /** σv of the last accepted sample; null = unknown → estimates are DEGRADED. */
    private var lastAcceptedSigmaMps: Float? = null

    val hasFix: Boolean get() = lastUpdateNanos != Long.MIN_VALUE

    /** Legacy entry point (σv unknown). Prefer [update] with a [GnssSpeedSample]. */
    fun update(
        elapsedRealtimeNanos: Long,
        measuredMps: Float,
    ) {
        updateInternal(elapsedRealtimeNanos, measuredMps, sigmaMps = null)
    }

    override fun update(sample: GnssSpeedSample): SampleRejection? =
        updateInternal(sample.fixTimeNanos, sample.speedMps, sample.speedSigmaMps)

    private fun updateInternal(
        elapsedRealtimeNanos: Long,
        measuredMps: Float,
        sigmaMps: Float?,
    ): SampleRejection? {
        if (measuredMps.isNaN()) return SampleRejection.NAN_SPEED
        val measured = measuredMps.coerceAtLeast(0f)

        if (lastUpdateNanos == Long.MIN_VALUE) {
            seed(elapsedRealtimeNanos, measured, sigmaMps)
            return null
        }
        val dt = (elapsedRealtimeNanos - lastUpdateNanos) / NANOS_PER_SECOND
        // [G1] Monotonic guard — never a negative/zero interval.
        if (dt <= 0f) return SampleRejection.NON_MONOTONIC_TIME
        if (dt > reseedAfterSeconds) {
            seed(elapsedRealtimeNanos, measured, sigmaMps)
            return null
        }

        val predicted = speedMps + accelMps2 * dt
        val residual = measured - predicted

        // [G4] Single-fix outlier: coast on the prediction once; a second
        // consecutive implausible fix is accepted as a real step change.
        // Pinned GPS-06: the two fixes' mutual coherence is not tested.
        if (abs(residual) / dt > maxPlausibleAccelMps2 && !pendingOutlier) {
            pendingOutlier = true
            speedMps = predicted.coerceAtLeast(0f)
            lastUpdateNanos = elapsedRealtimeNanos
            return SampleRejection.OUTLIER_COASTED
        }
        pendingOutlier = false

        // Pinned GPS-02: sigmaMps rides along for validity/logging but does
        // not weight the gain — GPS-2's Kalman fixes that.
        speedMps = (predicted + alpha * residual).coerceAtLeast(0f)
        accelMps2 = (accelMps2 + beta / dt * residual).coerceIn(-15f, 15f)
        lastUpdateNanos = elapsedRealtimeNanos
        lastAcceptedSigmaMps = sigmaMps
        return null
    }

    /** Speed prediction at [elapsedRealtimeNanos] — the per-FFT-frame read. */
    fun predictAt(elapsedRealtimeNanos: Long): Float {
        if (lastUpdateNanos == Long.MIN_VALUE) return 0f
        val dt =
            ((elapsedRealtimeNanos - lastUpdateNanos) / NANOS_PER_SECOND)
                .coerceIn(0f, maxPredictAheadSeconds)
        return (speedMps + accelMps2 * dt).coerceAtLeast(0f)
    }

    /**
     * [GPS-0.1] Full estimate with age and DESCRIPTIVE validity. The validity
     * thresholds are provisional (GPS-1.1 makes the horizon configurable and
     * enforced; GPS-2 derives validity from covariance). Nothing consumes the
     * validity yet — the frozen-number defect stays pinned [GPS-01].
     */
    override fun estimateAt(elapsedRealtimeNanos: Long): SpeedEstimate {
        if (lastUpdateNanos == Long.MIN_VALUE) {
            return SpeedEstimate(
                estimateTimeNanos = elapsedRealtimeNanos,
                lastFixTimeNanos = null,
                speedMps = 0f,
                accelerationMps2 = 0f,
                speedSigmaMps = null,
                accelerationSigmaMps2 = null,
                ageSinceFixNanos = null,
                validity = EstimateValidity.INVALID,
            )
        }
        val ageNanos = (elapsedRealtimeNanos - lastUpdateNanos).coerceAtLeast(0L)
        val horizonNanos = (maxPredictAheadSeconds * NANOS_PER_SECOND).toLong()
        val validity =
            when {
                ageNanos > horizonNanos -> EstimateValidity.INVALID
                lastAcceptedSigmaMps == null -> EstimateValidity.DEGRADED
                ageNanos <= FRESH_VALID_NANOS -> EstimateValidity.VALID
                else -> EstimateValidity.PREDICTED
            }
        return SpeedEstimate(
            estimateTimeNanos = elapsedRealtimeNanos,
            lastFixTimeNanos = lastUpdateNanos,
            speedMps = predictAt(elapsedRealtimeNanos),
            accelerationMps2 = accelMps2,
            // Pinned GPS-04: the α-β carries no covariance.
            speedSigmaMps = null,
            accelerationSigmaMps2 = null,
            ageSinceFixNanos = ageNanos,
            validity = validity,
        )
    }

    override fun reset() {
        speedMps = 0f
        accelMps2 = 0f
        lastUpdateNanos = Long.MIN_VALUE
        pendingOutlier = false
        lastAcceptedSigmaMps = null
    }

    private fun seed(
        nanos: Long,
        measured: Float,
        sigmaMps: Float?,
    ) {
        speedMps = measured
        accelMps2 = 0f
        lastUpdateNanos = nanos
        pendingOutlier = false
        lastAcceptedSigmaMps = sigmaMps
    }

    private companion object {
        const val NANOS_PER_SECOND = 1e9f

        /**
         * An estimate this close to its fix counts as VALID rather than
         * PREDICTED — well under half the nominal 1 Hz fix interval.
         * PROVISIONAL until GPS-2's covariance-based rule replaces it.
         */
        const val FRESH_VALID_NANOS = 350_000_000L
    }
}
