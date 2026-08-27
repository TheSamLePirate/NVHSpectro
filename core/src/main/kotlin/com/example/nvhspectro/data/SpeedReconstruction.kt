package com.example.nvhspectro.data

import com.example.nvhspectro.TelemetryData
import com.example.nvhspectro.WavAnalysis

/**
 * [plan-gps GPS-4.4] Speed reconstruction for DEFERRED analyses (WAV/video
 * replays, recorded reports): RTS smoothing over the sidecar's RAW fixes,
 * evaluated at each sample's audio instant.
 *
 * The recorder appends telemetry at frame rate (~43 fps) while GNSS fixes
 * arrive at ~1 Hz, so runs of samples share one fix — the smoother sees each
 * DISTINCT fix once, never the frame-rate copies, and never an already
 * extrapolated per-frame speed (those must not be recycled as truth).
 *
 * Sidecars without usable monotonic fix times (v1, or synthetic imports)
 * fall back to the historical corner interpolation; [Result.statusLabel]
 * says which path produced the speeds — the label the report must print
 * [Gate GPS-4].
 */
object SpeedReconstruction {
    /** Status labels for displays/reports [GPS-4.4]. */
    const val STATUS_SMOOTHED = "lissée (RTS)"
    const val STATUS_INTERPOLATED = "brute (interpolée)"

    /** Distinct fixes below this cannot anchor a meaningful smoothing pass. */
    const val MIN_FIXES_FOR_SMOOTHING = 3

    class Result(
        val telemetry: List<TelemetryData>,
        val statusLabel: String,
    )

    /**
     * Rebuild theoretical speeds (+σ) for [samples]. [audioTimesNanos] (v3
     * sidecars) gives each sample's audio BOOTTIME; without it, samples are
     * evaluated at their own fix time.
     */
    fun reconstruct(
        samples: List<TelemetryData>,
        audioTimesNanos: List<Long>?,
    ): Result {
        val fixes = distinctFixes(samples)
        val knots = if (fixes.size >= MIN_FIXES_FOR_SMOOTHING) RtsSpeedSmoother.smooth(fixes) else emptyList()
        if (knots.size < MIN_FIXES_FOR_SMOOTHING) {
            return Result(WavAnalysis.interpolateTheoreticalSpeed(samples), STATUS_INTERPOLATED)
        }
        val telemetry =
            samples.mapIndexed { i, sample ->
                val evalTime = audioTimesNanos?.getOrNull(i)?.takeIf { it > 0L } ?: sample.elapsedRealtimeNanos
                val eval = evalAt(knots, evalTime)
                sample.copy(
                    theoreticalSpeedKmh = (eval.speedMps * KMH_PER_MPS).toFloat(),
                    theoreticalSpeedSigmaKmh = (eval.speedSigmaMps * KMH_PER_MPS).toFloat(),
                    // Smoothed values are the best available for a replay; σ
                    // carries the honesty, the label carries the provenance.
                    speedValidity = EstimateValidity.VALID,
                )
            }
        return Result(telemetry, STATUS_SMOOTHED)
    }

    /** One [GnssSpeedSample] per distinct monotonic fix — frame-rate copies collapse. */
    private fun distinctFixes(samples: List<TelemetryData>): List<GnssSpeedSample> {
        val fixes = mutableListOf<GnssSpeedSample>()
        var lastNanos = Long.MIN_VALUE
        for (s in samples) {
            if (s.elapsedRealtimeNanos <= 0L || s.elapsedRealtimeNanos <= lastNanos) continue
            lastNanos = s.elapsedRealtimeNanos
            fixes.add(
                GnssSpeedSample(
                    fixTimeNanos = s.elapsedRealtimeNanos,
                    callbackTimeNanos = s.elapsedRealtimeNanos,
                    speedMps = s.speedKmh / KMH_PER_MPS,
                    speedSigmaMps = s.speedAccuracyMs.takeIf { it > 0f },
                    source = SpeedSampleSource.GPS,
                ),
            )
        }
        return fixes
    }

    private class Eval(
        val speedMps: Double,
        val speedSigmaMps: Double,
    )

    /** Piecewise evaluation between smoothed knots; clamped at the span ends. */
    private fun evalAt(
        knots: List<RtsSpeedSmoother.SmoothedPoint>,
        timeNanos: Long,
    ): Eval {
        val after = knots.indexOfFirst { it.timeNanos >= timeNanos }
        return when {
            after == 0 -> Eval(knots.first().speedMps, knots.first().speedSigmaMps)
            after < 0 -> Eval(knots.last().speedMps, knots.last().speedSigmaMps)
            else -> {
                val a = knots[after - 1]
                val b = knots[after]
                val span = (b.timeNanos - a.timeNanos).toDouble()
                val frac = if (span <= 0.0) 0.0 else (timeNanos - a.timeNanos) / span
                Eval(
                    speedMps = (a.speedMps + frac * (b.speedMps - a.speedMps)).coerceAtLeast(0.0),
                    speedSigmaMps = a.speedSigmaMps + frac * (b.speedSigmaMps - a.speedSigmaMps),
                )
            }
        }
    }

    private const val KMH_PER_MPS = 3.6f
}
