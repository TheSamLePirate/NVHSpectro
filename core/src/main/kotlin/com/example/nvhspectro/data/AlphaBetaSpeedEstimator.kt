package com.example.nvhspectro.data

import kotlin.math.abs

/**
 * α-β tracker over GNSS Doppler speed [audit G1–G4, plan 2.4].
 *
 * Purpose: turn ~1 Hz Doppler fixes into a continuously *predictable* speed
 * (v + a·dt) so the live order projection needs no lookahead — this is what
 * deletes the old 1.2 s display latency [L5]. Acceleration falls out of the
 * filter as a by-product, replacing the raw 1 Hz derivative whose wall-clock
 * dt made it spike on clock adjustments [G1]: all interval math here uses the
 * caller-supplied monotonic elapsedRealtimeNanos.
 *
 * Pure Kotlin — no Android imports — so it is JVM-unit-testable.
 *
 * Gains: α=0.5 with β=α²/(2−α)≈0.17 (critically damped pairing for ~1 Hz
 * updates). PROVISIONAL until tuned on the Phase 0.8 field drive logs.
 */
class AlphaBetaSpeedEstimator(
    private val alpha: Float = 0.5f,
    private val beta: Float = 0.17f,
    /** Residual implying more than this acceleration is a single-fix outlier [G4]. */
    private val maxPlausibleAccelMps2: Float = 12f,
    /** No fix for longer than this → re-seed instead of blending [G4]. */
    private val reseedAfterSeconds: Float = 3f,
    /** Never extrapolate a prediction further than this beyond the last fix. */
    private val maxPredictAheadSeconds: Float = 2f
) {
    /** Filtered speed in m/s at the time of the last accepted update. */
    var speedMps: Float = 0f
        private set

    /** Filtered acceleration in m/s². */
    var accelMps2: Float = 0f
        private set

    private var lastUpdateNanos = Long.MIN_VALUE
    private var pendingOutlier = false

    val hasFix: Boolean get() = lastUpdateNanos != Long.MIN_VALUE

    /** Feed one Doppler measurement. NaN and non-monotonic timestamps are ignored. */
    fun update(elapsedRealtimeNanos: Long, measuredMps: Float) {
        if (measuredMps.isNaN()) return
        val measured = measuredMps.coerceAtLeast(0f)

        if (lastUpdateNanos == Long.MIN_VALUE) {
            seed(elapsedRealtimeNanos, measured)
            return
        }
        val dt = (elapsedRealtimeNanos - lastUpdateNanos) / 1e9f
        if (dt <= 0f) return // [G1] monotonic guard — never a negative/zero interval
        if (dt > reseedAfterSeconds) {
            seed(elapsedRealtimeNanos, measured)
            return
        }

        val predicted = speedMps + accelMps2 * dt
        val residual = measured - predicted

        // [G4] Single-fix outlier: coast on the prediction once; a second
        // consecutive implausible fix is accepted as a real step change.
        if (abs(residual) / dt > maxPlausibleAccelMps2 && !pendingOutlier) {
            pendingOutlier = true
            speedMps = predicted.coerceAtLeast(0f)
            lastUpdateNanos = elapsedRealtimeNanos
            return
        }
        pendingOutlier = false

        speedMps = (predicted + alpha * residual).coerceAtLeast(0f)
        accelMps2 = (accelMps2 + beta / dt * residual).coerceIn(-15f, 15f)
        lastUpdateNanos = elapsedRealtimeNanos
    }

    /** Speed prediction at [elapsedRealtimeNanos] — the per-FFT-frame read. */
    fun predictAt(elapsedRealtimeNanos: Long): Float {
        if (lastUpdateNanos == Long.MIN_VALUE) return 0f
        val dt = ((elapsedRealtimeNanos - lastUpdateNanos) / 1e9f)
            .coerceIn(0f, maxPredictAheadSeconds)
        return (speedMps + accelMps2 * dt).coerceAtLeast(0f)
    }

    fun reset() {
        speedMps = 0f
        accelMps2 = 0f
        lastUpdateNanos = Long.MIN_VALUE
        pendingOutlier = false
    }

    private fun seed(nanos: Long, measured: Float) {
        speedMps = measured
        accelMps2 = 0f
        lastUpdateNanos = nanos
        pendingOutlier = false
    }
}
