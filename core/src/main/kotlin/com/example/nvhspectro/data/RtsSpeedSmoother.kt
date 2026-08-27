package com.example.nvhspectro.data

import kotlin.math.max
import kotlin.math.sqrt

/**
 * [plan-gps GPS-4.4] Deferred speed reconstruction: a forward Kalman pass
 * (same model and [KalmanSpeedEstimator.Config] parameters as the LIVE
 * estimator) followed by a backward Rauch–Tung–Striebel pass.
 *
 * A recorded analysis has the FUTURE fixes too — the smoothed trajectory uses
 * them, which the causal LIVE filter never may [plan-gps §2]. Statistically
 * implausible fixes (NIS gate) are simply dropped offline; a gap longer than
 * the re-seed threshold splits the trace into independently smoothed segments
 * (no information crosses a re-seed boundary). Frame-by-frame EXTRAPOLATED
 * speeds are never fed back in as truth — input is raw fixes only.
 *
 * Units and time bases [GPS-0.5]: m/s, m/s², BOOTTIME nanoseconds.
 * Pure Kotlin — JVM-unit-testable.
 */
object RtsSpeedSmoother {
    /** One smoothed state knot at a fix instant. */
    class SmoothedPoint(
        val timeNanos: Long,
        val speedMps: Double,
        val accelMps2: Double,
        val speedSigmaMps: Double,
    )

    /** A [v, a] state with symmetric covariance (p11, p12, p22). */
    private class State(
        var v: Double,
        var a: Double,
        var p11: Double,
        var p12: Double,
        var p22: Double,
    )

    private class Step(
        val timeNanos: Long,
        val dtFromPrev: Double,
        /** Predicted state BEFORE this fix's measurement update. */
        val prior: State,
        /** Filtered state, overwritten by the backward pass with the smoothed one. */
        val state: State,
    )

    /** One raw fix reduced to its measurement (z, R = σ²). */
    private class Measurement(
        val z: Double,
        val r: Double,
    )

    /**
     * Smooth timestamped raw fixes. Returns knots at every ACCEPTED fix time,
     * in input order; rejected/degenerate fixes produce no knot.
     */
    fun smooth(
        samples: List<GnssSpeedSample>,
        config: KalmanSpeedEstimator.Config = KalmanSpeedEstimator.Config(),
    ): List<SmoothedPoint> {
        val out = mutableListOf<SmoothedPoint>()
        var segment = mutableListOf<Step>()
        for (sample in samples) {
            segment = consumeSample(sample, segment, out, config)
        }
        flushSegment(segment, out, config)
        return out
    }

    /** Returns the (possibly re-seeded) current segment after one sample. */
    private fun consumeSample(
        sample: GnssSpeedSample,
        segment: MutableList<Step>,
        out: MutableList<SmoothedPoint>,
        config: KalmanSpeedEstimator.Config,
    ): MutableList<Step> {
        if (!sample.speedMps.isFinite()) return segment
        val sigma = max(sample.speedSigmaMps?.toDouble() ?: config.defaultSigmaMps, config.sigmaFloorMps)
        val m = Measurement(sample.speedMps.toDouble().coerceAtLeast(0.0), sigma * sigma)
        val prev = segment.lastOrNull()
        val dt = if (prev == null) 0.0 else (sample.fixTimeNanos - prev.timeNanos) / NANOS_PER_SECOND
        return when {
            prev != null && dt <= 0.0 -> segment // non-monotonic: drop
            prev == null || dt > config.reseedAfterSeconds -> {
                // Segment boundary: smooth what we have, then re-seed.
                flushSegment(segment, out, config)
                mutableListOf(seedStep(sample.fixTimeNanos, m, config))
            }
            else -> {
                forwardStep(segment, sample.fixTimeNanos, dt, m, config)
                segment
            }
        }
    }

    private fun flushSegment(
        segment: List<Step>,
        out: MutableList<SmoothedPoint>,
        config: KalmanSpeedEstimator.Config,
    ) {
        if (segment.isEmpty()) return
        backwardPass(segment, config)
        segment.forEach { out.add(it.toSmoothedPoint()) }
    }

    private fun seedStep(
        timeNanos: Long,
        m: Measurement,
        config: KalmanSpeedEstimator.Config,
    ): Step {
        val pa = config.seedAccelSigmaMps2 * config.seedAccelSigmaMps2
        return Step(
            timeNanos,
            0.0,
            prior = State(m.z, 0.0, m.r, 0.0, pa),
            state = State(m.z, 0.0, m.r, 0.0, pa),
        )
    }

    /** The forward predict + NIS gate + Joseph update — the LIVE filter's math. */
    private fun forwardStep(
        segment: MutableList<Step>,
        timeNanos: Long,
        dt: Double,
        m: Measurement,
        config: KalmanSpeedEstimator.Config,
    ) {
        val prev = segment.last().state
        val q = config.jerkPsd
        val vPred = prev.v + prev.a * dt
        val pp11 = prev.p11 + 2.0 * dt * prev.p12 + dt * dt * prev.p22 + q * dt * dt * dt / Q11_DENOMINATOR
        val pp12 = prev.p12 + dt * prev.p22 + q * dt * dt / 2.0
        val pp22 = prev.p22 + q * dt

        val y = m.z - vPred
        val s = pp11 + m.r
        if (y * y / s > config.nisRejectThreshold) return // offline: outliers just drop

        val k1 = pp11 / s
        val k2 = pp12 / s
        val oneMinusK1 = 1.0 - k1
        segment.add(
            Step(
                timeNanos = timeNanos,
                dtFromPrev = dt,
                prior = State(vPred, prev.a, pp11, pp12, pp22),
                state =
                    State(
                        v = vPred + k1 * y,
                        a = prev.a + k2 * y,
                        p11 = oneMinusK1 * oneMinusK1 * pp11 + k1 * k1 * m.r,
                        p12 = oneMinusK1 * (pp12 - k2 * pp11) + k1 * k2 * m.r,
                        p22 = k2 * k2 * pp11 - 2.0 * k2 * pp12 + pp22 + k2 * k2 * m.r,
                    ),
            ),
        )
    }

    /**
     * RTS: C = P⁺Fᵀ(P⁻ₖ₊₁)⁻¹; xₛ = x⁺ + C(xₛₖ₊₁ − x⁻ₖ₊₁);
     * Pₛ = P⁺ + C(Pₛₖ₊₁ − P⁻ₖ₊₁)Cᵀ. Steps are mutated to their smoothed values.
     */
    private fun backwardPass(
        segment: List<Step>,
        config: KalmanSpeedEstimator.Config,
    ) {
        for (k in segment.size - 2 downTo 0) {
            val cur = segment[k].state
            val next = segment[k + 1]
            val dt = next.dtFromPrev
            val prior = next.prior
            val smoothedNext = next.state
            // P⁺Fᵀ with Fᵀ = [[1, 0], [dt, 1]] — not symmetric.
            val g11 = cur.p11 + dt * cur.p12
            val g12 = cur.p12
            val g21 = cur.p12 + dt * cur.p22
            val g22 = cur.p22
            val det = prior.p11 * prior.p22 - prior.p12 * prior.p12
            if (det <= MIN_INVERTIBLE_DET) continue // degenerate prior: keep the filtered value
            val i11 = prior.p22 / det
            val i12 = -prior.p12 / det
            val i22 = prior.p11 / det
            val c11 = g11 * i11 + g12 * i12
            val c12 = g11 * i12 + g12 * i22
            val c21 = g21 * i11 + g22 * i12
            val c22 = g21 * i12 + g22 * i22

            val dv = smoothedNext.v - prior.v
            val da = smoothedNext.a - prior.a
            cur.v = (cur.v + c11 * dv + c12 * da).coerceAtLeast(0.0)
            cur.a += c21 * dv + c22 * da

            // ΔP = Pₛₖ₊₁ − P⁻ₖ₊₁ (symmetric); Pₛ = P⁺ + CΔPCᵀ.
            val d11 = smoothedNext.p11 - prior.p11
            val d12 = smoothedNext.p12 - prior.p12
            val d22 = smoothedNext.p22 - prior.p22
            val t11 = c11 * d11 + c12 * d12
            val t12 = c11 * d12 + c12 * d22
            val t21 = c21 * d11 + c22 * d12
            val t22 = c21 * d12 + c22 * d22
            cur.p11 = max(cur.p11 + t11 * c11 + t12 * c12, config.sigmaFloorMps * config.sigmaFloorMps)
            cur.p12 += t11 * c21 + t12 * c22
            cur.p22 = max(cur.p22 + t21 * c21 + t22 * c22, 0.0)
        }
    }

    private fun Step.toSmoothedPoint(): SmoothedPoint =
        SmoothedPoint(
            timeNanos = timeNanos,
            speedMps = state.v,
            accelMps2 = state.a,
            speedSigmaMps = sqrt(max(state.p11, 0.0)),
        )

    private const val NANOS_PER_SECOND = 1e9
    private const val Q11_DENOMINATOR = 3.0
    private const val MIN_INVERTIBLE_DET = 1e-12
}
