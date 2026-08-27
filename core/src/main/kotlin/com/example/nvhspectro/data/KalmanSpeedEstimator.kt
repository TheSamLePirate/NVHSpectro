package com.example.nvhspectro.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * [plan-gps GPS-2] Linear Kalman filter over GNSS Doppler speed — the
 * uncertainty-aware replacement for the fixed-gain α-β tracker
 * (closes GPS-02, GPS-04, GPS-05, GPS-06, GPS-14 estimator-side).
 *
 * Model:
 * ```
 * x = [v, a]              F(dt) = [[1, dt], [0, 1]]
 * z = Android GNSS speed  H = [1, 0]      R = max(σv, floor)²
 * Q(dt) = q · [[dt³/3, dt²/2], [dt²/2, dt]]   (white-jerk PSD q)
 * ```
 * - Double precision core; covariance kept symmetric via a Joseph-form
 *   update; variable dt [GPS-2.1].
 * - The declared σv weights every update (R = σv²); a fix without σv gets
 *   the conservative [Config.defaultSigmaMps] and marks estimates DEGRADED
 *   [GPS-02, GPS-1.3].
 * - Robust rejection [GPS-2.2]: the normalized innovation NIS = y²/S gates
 *   each fix. A rejected fix leaves the state at its last accepted epoch, so
 *   prediction uncertainty keeps growing normally. Reacquisition needs two
 *   MUTUALLY COHERENT rejected fixes (implied acceleration plausible), a
 *   rejection streak (safety valve), or a gap long enough for a full re-seed.
 * - Stationary state with hysteresis [GPS-2.3]: near zero the published
 *   speed/acceleration are an honest 0 instead of estimation flicker; the
 *   internal state is never silently saturated (no clamp substitutes for
 *   validity).
 *
 * Units and time bases [GPS-0.5]: m/s, m/s², BOOTTIME nanoseconds.
 * Pure Kotlin — JVM-unit-testable. All parameters in [Config] are
 * PROVISIONAL until the Gate GPS-5 field-tuning campaign.
 */
class KalmanSpeedEstimator(
    private val config: Config = Config(),
) : SpeedEstimator {
    /** Named, versionable parameters [GPS-2.1]; provisional until Gate GPS-5. */
    data class Config(
        /**
         * White-jerk power spectral density q, (m/s²)²/s. At the nominal 1 Hz
         * fix rate and σv = 0.5 m/s this puts the predicted σv at ≈ 1.0 m/s
         * one second after a fix and ≈ 1.5 m/s at 1.5 s — PREDICTED up to
         * ~1.5 s, DEGRADED until the 2 s horizon.
         */
        val jerkPsd: Double = 0.5,
        /** R floor: guards against optimistic chipset σv claims. */
        val sigmaFloorMps: Double = 0.1,
        /** Conservative σv for fixes that report none [GPS-1.3]; marks DEGRADED. */
        val defaultSigmaMps: Double = 2.0,
        /** NIS gate, χ²(1): 9.0 ≈ a 3σ innovation [GPS-2.2]. */
        val nisRejectThreshold: Double = 9.0,
        /** Two rejected fixes implying more than this are NOT mutually coherent. */
        val maxPlausibleAccelMps2: Double = 12.0,
        /** A reacquisition candidate older than this cannot pair with a new fix. */
        val maxCandidateAgeSeconds: Double = 2.5,
        /** Rejection streak that forces a re-seed (sustained real change). */
        val maxConsecutiveRejections: Int = 4,
        /** No accepted fix for longer than this → re-seed instead of predicting. */
        val reseedAfterSeconds: Double = 5.0,
        /** Numeric prediction is frozen at this age; validity turns INVALID past it. */
        val predictionHorizonSeconds: Double = 2.0,
        /** Age below which an estimate is VALID rather than PREDICTED. */
        val freshValidNanos: Long = 350_000_000L,
        /** Predicted σv beyond this is metrologically unusable → INVALID [GPS-1.1]. */
        val invalidSigmaMps: Double = 3.0,
        /** Predicted σv beyond this (or a defaulted σv) → DEGRADED. */
        val degradedSigmaMps: Double = 1.5,
        /** Initial acceleration σ on (re-)seed. */
        val seedAccelSigmaMps2: Double = 2.0,
        /** Enter the stationary state below this speed… */
        val stationaryEnterMps: Double = 0.25,
        /** …leave it above this one (hysteresis) [GPS-2.3]. */
        val stationaryExitMps: Double = 0.6,
    )

    // State [v, a] and symmetric covariance at the last ACCEPTED fix epoch.
    private var v = 0.0
    private var a = 0.0
    private var p11 = 0.0
    private var p12 = 0.0
    private var p22 = 0.0
    private var lastFixNanos = Long.MIN_VALUE
    private var lastSigmaDefaulted = false
    private var stationary = false

    // Reacquisition candidate: the most recent REJECTED fix [GPS-2.2].
    private var candidateSpeed = 0.0
    private var candidateNanos = Long.MIN_VALUE
    private var consecutiveRejections = 0

    /** Last computed normalized innovation — diagnostic for traces/tuning [GPS-2.2]. */
    override var lastNis: Double? = null
        private set

    /** [GPS-4.3] Full parameter set rides along — traces stay comparable across tunings. */
    override val description: String = "kalman-va/1 $config"

    override fun update(sample: GnssSpeedSample): SampleRejection? {
        val z = sample.speedMps.toDouble().coerceAtLeast(0.0)
        val sigmaDefaulted = sample.speedSigmaMps == null
        val sigma = max(sample.speedSigmaMps?.toDouble() ?: config.defaultSigmaMps, config.sigmaFloorMps)
        val r = sigma * sigma
        val dt =
            if (lastFixNanos == Long.MIN_VALUE) {
                Double.MAX_VALUE // no epoch yet → the seed branch below
            } else {
                (sample.fixTimeNanos - lastFixNanos) / NANOS_PER_SECOND
            }
        return when {
            !sample.speedMps.isFinite() -> SampleRejection.NON_FINITE_SPEED
            dt > config.reseedAfterSeconds -> {
                seed(sample.fixTimeNanos, z, r, sigmaDefaulted)
                null
            }
            dt <= 0.0 -> SampleRejection.NON_MONOTONIC_TIME
            else -> gatedMeasurementUpdate(sample.fixTimeNanos, z, r, sigmaDefaulted, dt)
        }
    }

    /** Predict over [dt], gate on NIS, then Joseph-update [GPS-2.1, GPS-2.2]. */
    private fun gatedMeasurementUpdate(
        fixNanos: Long,
        z: Double,
        r: Double,
        sigmaDefaulted: Boolean,
        dt: Double,
    ): SampleRejection? {
        // Time update (on locals — committed only if the fix is accepted, so a
        // rejected fix leaves the epoch behind and uncertainty keeps growing).
        val q = config.jerkPsd
        val vPred = v + a * dt
        val q11 = q * dt * dt * dt / Q11_DENOMINATOR
        val q12 = q * dt * dt / 2.0
        val pp11 = p11 + 2.0 * dt * p12 + dt * dt * p22 + q11
        val pp12 = p12 + dt * p22 + q12
        val pp22 = p22 + q * dt

        // Innovation gate [GPS-2.2].
        val y = z - vPred
        val s = pp11 + r
        val nis = y * y / s
        lastNis = nis
        if (nis > config.nisRejectThreshold) {
            return handleRejectedFix(fixNanos, z, r, sigmaDefaulted)
        }
        candidateNanos = Long.MIN_VALUE
        consecutiveRejections = 0

        // Joseph-form measurement update [GPS-2.1].
        val k1 = pp11 / s
        val k2 = pp12 / s
        v = vPred + k1 * y
        a += k2 * y
        val oneMinusK1 = 1.0 - k1
        p11 = oneMinusK1 * oneMinusK1 * pp11 + k1 * k1 * r
        p12 = oneMinusK1 * (pp12 - k2 * pp11) + k1 * k2 * r
        p22 = k2 * k2 * pp11 - 2.0 * k2 * pp12 + pp22 + k2 * k2 * r
        lastFixNanos = fixNanos
        lastSigmaDefaulted = sigmaDefaulted
        updateStationary()
        return null
    }

    /**
     * [GPS-2.2] A statistically implausible fix. Reacquire only via a pair of
     * mutually coherent rejected fixes, a rejection streak, or (elsewhere) a
     * full re-seed after a long gap — never a blind second acceptance.
     */
    private fun handleRejectedFix(
        fixNanos: Long,
        z: Double,
        r: Double,
        sigmaDefaulted: Boolean,
    ): SampleRejection? {
        val candidateAge = (fixNanos - candidateNanos) / NANOS_PER_SECOND
        // Two mutually coherent implausible fixes = a real step change.
        val coherentWithCandidate =
            candidateNanos != Long.MIN_VALUE &&
                candidateAge in 0.0..config.maxCandidateAgeSeconds &&
                abs(z - candidateSpeed) / candidateAge <= config.maxPlausibleAccelMps2
        if (coherentWithCandidate) {
            seed(fixNanos, z, r, sigmaDefaulted)
            return null
        }
        candidateSpeed = z
        candidateNanos = fixNanos
        consecutiveRejections++
        return if (consecutiveRejections >= config.maxConsecutiveRejections) {
            // Safety valve: a sustained change must not be rejected forever.
            seed(fixNanos, z, r, sigmaDefaulted)
            null
        } else {
            SampleRejection.OUTLIER_REJECTED
        }
    }

    override fun estimateAt(elapsedRealtimeNanos: Long): SpeedEstimate {
        if (lastFixNanos == Long.MIN_VALUE) {
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
        val ageNanos = (elapsedRealtimeNanos - lastFixNanos).coerceAtLeast(0L)
        val horizonNanos = (config.predictionHorizonSeconds * NANOS_PER_SECOND).toLong()
        // The numeric prediction freezes at the horizon — it is diagnostic
        // only once INVALID [GPS-D4]; consumers gate on validity.
        val dt = (ageNanos.coerceAtMost(horizonNanos)) / NANOS_PER_SECOND
        val q = config.jerkPsd
        val vPred = v + a * dt
        val varV = p11 + 2.0 * dt * p12 + dt * dt * p22 + q * dt * dt * dt / Q11_DENOMINATOR
        val varA = p22 + q * dt
        val sigmaV = sqrt(max(varV, 0.0))
        val sigmaA = sqrt(max(varA, 0.0))
        val validity =
            when {
                ageNanos > horizonNanos -> EstimateValidity.INVALID
                sigmaV > config.invalidSigmaMps -> EstimateValidity.INVALID
                lastSigmaDefaulted || sigmaV > config.degradedSigmaMps -> EstimateValidity.DEGRADED
                ageNanos <= config.freshValidNanos -> EstimateValidity.VALID
                else -> EstimateValidity.PREDICTED
            }
        // [GPS-2.3] Stationary: publish an honest zero instead of near-zero
        // estimation flicker; σ stays truthful. No internal state is clamped.
        val publishV = if (stationary) 0.0 else vPred.coerceAtLeast(0.0)
        val publishA = if (stationary) 0.0 else a
        return SpeedEstimate(
            estimateTimeNanos = elapsedRealtimeNanos,
            lastFixTimeNanos = lastFixNanos,
            speedMps = publishV.toFloat(),
            accelerationMps2 = publishA.toFloat(),
            speedSigmaMps = sigmaV.toFloat(),
            accelerationSigmaMps2 = sigmaA.toFloat(),
            ageSinceFixNanos = ageNanos,
            validity = validity,
        )
    }

    override fun reset() {
        v = 0.0
        a = 0.0
        p11 = 0.0
        p12 = 0.0
        p22 = 0.0
        lastFixNanos = Long.MIN_VALUE
        lastSigmaDefaulted = false
        stationary = false
        candidateSpeed = 0.0
        candidateNanos = Long.MIN_VALUE
        consecutiveRejections = 0
        lastNis = null
    }

    private fun seed(
        nanos: Long,
        z: Double,
        r: Double,
        sigmaDefaulted: Boolean,
    ) {
        v = z
        a = 0.0
        p11 = r
        p12 = 0.0
        p22 = config.seedAccelSigmaMps2 * config.seedAccelSigmaMps2
        lastFixNanos = nanos
        lastSigmaDefaulted = sigmaDefaulted
        candidateNanos = Long.MIN_VALUE
        consecutiveRejections = 0
        updateStationary()
    }

    /** [GPS-2.3] Hysteresis so stop/creep noise cannot flicker the state. */
    private fun updateStationary() {
        stationary = if (stationary) v < config.stationaryExitMps else v < config.stationaryEnterMps
    }

    private companion object {
        const val NANOS_PER_SECOND = 1e9

        /** White jerk doubly integrated: Q₁₁ = q·dt³/3 (CV-model standard form). */
        const val Q11_DENOMINATOR = 3.0
    }
}
