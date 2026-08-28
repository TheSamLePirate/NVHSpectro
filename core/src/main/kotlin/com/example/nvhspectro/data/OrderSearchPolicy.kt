package com.example.nvhspectro.data

import kotlin.math.ceil

/**
 * [plan-gps GPS-4.1, GPS-4.2] Kinematic error budget and the dynamic
 * tracked-order search window it implies (closes GPS-10).
 *
 * Error budget (speeds in km/h):
 * ```
 * rpm    = v · 1000 / V1000          σrpm    = σv · 1000 / V1000
 * f(Hn)  = n · rpm / 60              σf(Hn)  = n · σrpm / 60
 * ```
 * The historical search radius was a FIXED ±1 bin (±Δf) while σf(H18) at the
 * default V1000 and a 0.5 m/s speed σ is ≈ 54 Hz — the true line usually sat
 * OUTSIDE the window and the tracker read noise at the projected bin. The
 * window is now built from the actual uncertainty: k·σf + Δf, and BOUNDED —
 * a window wider than the bound cannot pick one order's line apart from its
 * neighbours', so tracking is SUSPENDED ("ordre non identifiable") instead of
 * silently reporting an ambiguous value.
 *
 * V1000's own uncertainty (dynamic tire radius, gear-ratio rounding) is NOT
 * modeled here — it can dominate the GNSS term and is characterized
 * separately in the plan-5.3 validation document.
 *
 * When σ is UNKNOWN (α-β estimator, pre-v3 sidecars) the policy falls back
 * to the historical fixed radius — legacy analyses keep their behavior and
 * are marked "incertitude inconnue" by their DEGRADED validity instead.
 *
 * All constants PROVISIONAL until the Gate GPS-5 campaign.
 */
object OrderSearchPolicy {
    /** Confidence multiple on σf — k = 2 ≈ a 95 % band [GPS-4.2]; recorded in exports. */
    const val CONFIDENCE_K = 2.0

    /**
     * Identifiability bound: the half-window may not exceed half the spacing
     * to the adjacent integer order (spacing = h1). Wider than that, the
     * search would sweep the neighbouring orders' lines.
     */
    const val MAX_HALF_WIDTH_FRACTION_OF_H1 = 0.5

    /** V1000 is "km/h at 1000 rpm" — the scale of the rpm conversion. */
    private const val RPM_REFERENCE = 1000.0
    private const val MIN_V1000_KMH = 0.1
    private const val SECONDS_PER_MINUTE = 60.0

    /** σrpm from the speed σ, both the plan-gps GPS-4.1 formulas. */
    fun sigmaRpm(
        sigmaSpeedKmh: Double,
        v1000Kmh: Double,
    ): Double = sigmaSpeedKmh * RPM_REFERENCE / v1000Kmh.coerceAtLeast(MIN_V1000_KMH)

    /** σ of order [order]'s frequency, Hz. */
    fun sigmaOrderFreqHz(
        order: Double,
        sigmaSpeedKmh: Double,
        v1000Kmh: Double,
    ): Double = order * sigmaRpm(sigmaSpeedKmh, v1000Kmh) / SECONDS_PER_MINUTE

    /** The window the tracked-order search must use, or a suspension. */
    class Window(
        val halfWidthHz: Double,
        /** Bins for OrderTrackingEngine.searchTrackedOrder (≥ 1). */
        val radiusBins: Int,
        /** σf behind this window; null = unknown uncertainty (legacy fallback). */
        val sigmaFreqHz: Double?,
        /** False → suspend automatic tracking and display "non identifiable" [GPS-4.2]. */
        val identifiable: Boolean,
    )

    /**
     * Build the search window from the tracked order's frequency σ (from
     * [sigmaOrderFreqHz]) at fundamental [h1FreqHz]. [sigmaFreqHz] null =
     * uncertainty unknown → the historical [legacyRadiusBins] window, always
     * considered identifiable (legacy behavior, honestly labeled by the
     * estimate's DEGRADED validity).
     */
    fun windowFor(
        sigmaFreqHz: Double?,
        h1FreqHz: Double,
        dfHz: Double,
        legacyRadiusBins: Int,
    ): Window {
        if (sigmaFreqHz == null || dfHz <= 0.0 || h1FreqHz <= 0.0) {
            return Window(
                halfWidthHz = legacyRadiusBins * dfHz,
                radiusBins = legacyRadiusBins,
                sigmaFreqHz = null,
                identifiable = true,
            )
        }
        val sigmaF = sigmaFreqHz
        val halfWidthHz = CONFIDENCE_K * sigmaF + dfHz
        val boundHz = MAX_HALF_WIDTH_FRACTION_OF_H1 * h1FreqHz
        // The bound gates the UNCERTAINTY term only: Δf is grid resolution,
        // present in every search regardless of how good the speed is. (When
        // Δf itself dwarfs h1, adjacent orders share bins — a display-
        // resolution matter the FFT-size choice owns, not a speed problem.)
        return Window(
            halfWidthHz = halfWidthHz,
            radiusBins = ceil(halfWidthHz / dfHz).toInt().coerceAtLeast(1),
            sigmaFreqHz = sigmaF,
            identifiable = CONFIDENCE_K * sigmaF <= boundHz,
        )
    }
}
