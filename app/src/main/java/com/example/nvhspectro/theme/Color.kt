// NVH Spectro colour tokens — fixed dark professional instrument palette [U5, plan 4.3, D4].
//
// Decision D4: the app is a measurement instrument whose central surface is a black
// spectrogram canvas, used in vehicle cabins in both daylight and night. A single calibrated
// dark theme is therefore the product decision; dynamic (wallpaper) colour and the system
// light theme are deliberately NOT followed — a wallpaper-tinted or light surface changes how
// an operator reads a colour-mapped measurement.
//
// Contrast: every foreground token below is checked against the surface it is used on and
// clears WCAG AA (≥ 4.5:1 for body text, ≥ 3:1 for large text and non-text indicators). The
// measured ratios are stated per token and enforced by `PaletteContrastTest` — recompute them
// (the test will tell you) if a value changes.
//
// This file IS the project's colour constants: every literal here is a named, documented
// token, and `ci/checks.sh` fails the build if a hex appears anywhere else. MagicNumber is
// therefore suppressed for this file only — nowhere else.
@file:Suppress("MagicNumber")

package com.example.nvhspectro.theme

import androidx.compose.ui.graphics.Color

// --- Neutral surfaces: near-black, so the canvas is the brightest thing on screen --------

/** App window background; also the XML `windowBackground` (kills the white launch flash). */
val NvhBackground = Color(0xFF0A0E13)

/** Default card / dialog surface. */
val NvhSurface = Color(0xFF141A22)

/** Raised surface: telemetry card, dialog sections. */
val NvhSurfaceVariant = Color(0xFF1C2430)

/** Highest container: selected rows, table headers. */
val NvhSurfaceContainerHigh = Color(0xFF232D3B)

/** Primary text. 14.9:1 on [NvhSurface]. */
val NvhOnSurface = Color(0xFFE6EDF3)

/** Secondary text and axis labels. 7.6:1 on [NvhSurface], 6.6:1 on [NvhSurfaceVariant]. */
val NvhOnSurfaceVariant = Color(0xFFA9B6C4)

/** Hairlines, dividers, chip borders (non-text: 3:1 bar does not apply to decoration). */
val NvhOutline = Color(0xFF3B4756)

/** Disabled control container. */
val NvhDisabledContainer = Color(0xFF2A323D)

/** Disabled control label. 5.2:1 on [NvhDisabledContainer]. */
val NvhDisabledContent = Color(0xFF99A6B5)

// ---------------------------------------------------------------------------------------
// Brand / interactive
// ---------------------------------------------------------------------------------------

/** Instrument blue — primary action colour. 8.9:1 on [NvhBackground]. */
val NvhPrimary = Color(0xFF4FC3F7)

/** Text/icon on [NvhPrimary]. 8.2:1 on primary. */
val NvhOnPrimary = Color(0xFF00293D)

/** Top app bar container. */
val NvhPrimaryContainer = Color(0xFF13324A)

/** Text on [NvhPrimaryContainer]. 9.1:1. */
val NvhOnPrimaryContainer = Color(0xFFCCE7F8)

/** Secondary action (freeze/measure controls). 8.1:1 on [NvhBackground]. */
val NvhSecondary = Color(0xFF9FB4C8)
val NvhOnSecondary = Color(0xFF12232F)
val NvhSecondaryContainer = Color(0xFF25313D)
val NvhOnSecondaryContainer = Color(0xFFD3E1EE)

val NvhTertiary = Color(0xFF80CBC4)
val NvhOnTertiary = Color(0xFF00332E)

/** Error text/icon. 5.9:1 on [NvhSurface]. */
val NvhError = Color(0xFFFF8A80)
val NvhOnError = Color(0xFF3A0906)
val NvhErrorContainer = Color(0xFF5C1A16)
val NvhOnErrorContainer = Color(0xFFFFDAD6)

// ---------------------------------------------------------------------------------------
// Instrument semantics — status, modes and overlays.
//
// These are NOT decorative: each one encodes measurement state. Colour is never the only
// channel (plan 4.4 pairs each with a shape or a text label) but the hues are fixed here so
// screen, canvas overlays and the PDF report agree [U7].
// ---------------------------------------------------------------------------------------

/** GNSS fix usable / kinematics active. 9.4:1 on [NvhSurfaceVariant]. */
val NvhStatusGood = Color(0xFF00E676)

/** Degraded but usable. 8.9:1 on [NvhSurfaceVariant]. */
val NvhStatusWarn = Color(0xFFFFA726)

/** Unusable / lost / stopped. 6.1:1 on [NvhSurfaceVariant]. */
val NvhStatusBad = Color(0xFFFF5252)

/** Target-order highlight (harmonic whitelist, H1 overlay). 11.2:1 on [NvhBackground]. */
val NvhAccent = Color(0xFF00E5FF)

/** Predicted / theoretical (model-derived, not directly measured) quantities. */
val NvhTheoretical = Color(0xFF64B5F6)

/** Live capture source mode. 6.2:1 under [NvhOnSurface]. */
val NvhModeLive = Color(0xFF673AB7)

/**
 * WAV analyzer source mode — **filled control** container. 4.3:1 under [NvhOnSurface].
 *
 * Split from [NvhModeWavAccent] because one orange cannot serve both duties: a fill dark
 * enough to carry a light label is too dark to read as a hairline cursor on the black canvas,
 * and vice versa. `PaletteContrastTest` holds both ends.
 */
val NvhModeWav = Color(0xFFB45309)

/** WAV analyzer accent — text, playhead cursor and hairlines on dark surfaces. 7.3:1+. */
val NvhModeWavAccent = Color(0xFFF59E0B)

/** Video source mode — filled control. 4.9:1 under [NvhOnSurface]. */
val NvhModeVideo = Color(0xFF1565C0)

/** Video accent — text and hairlines on dark surfaces. 5.9:1+. */
val NvhModeVideoAccent = Color(0xFF42A5F5)

/** Recording in progress / frozen view (attention state) — filled control. 4.8:1. */
val NvhRecording = Color(0xFFC62828)

/** Manual report mode — filled control. 5.0:1 under [NvhOnSurface]. */
val NvhReportMode = Color(0xFFC2185B)

/** Report/filter accent — text and borders on dark surfaces. 5.1:1+. */
val NvhReportAccent = Color(0xFFF06292)

/** Export action — filled control. 4.6:1 under [NvhOnSurface]. */
val NvhExport = Color(0xFF026FA8)

/** A feature is switched on (GMPe engaged, order active). 6.5:1 under [NvhOnSurface]. */
val NvhActiveContainer = Color(0xFF1E6023)

/** An available-but-unselected control (mode buttons in the source menu). */
val NvhInactiveContainer = Color(0xFF334155)

/** Analysis canvas background — the measurement surface itself. */
val NvhCanvas = Color(0xFF000000)

/**
 * Scrim behind canvas overlays (banners and chips floating on the spectrogram).
 *
 * One value replaces the previous four near-identical translucent greys; it is deliberately
 * the most opaque of them, because these labels sit on a colour-mapped measurement and must
 * stay legible over the brightest part of the map.
 */
val NvhCanvasScrim = Color(0xE6121820)

/** Stronger scrim for modal-ish canvas panels (progress, empty state). */
val NvhCanvasPanel = Color(0xF00F172A)

/** Border for chips and badges drawn over the canvas. */
val NvhCanvasChipBorder = Color(0x66FFFFFF)

/** Container for a titled section inside a dialog. */
val NvhSectionContainer = Color(0xFF101827)

/** Instantaneous-spectrum trace in the 2D graph. */
val NvhSpectrum = Color(0xFFD500F9)

/** DSP audio-filter feature — filled control (shares the report hue). */
val NvhFilter = NvhReportMode

/** DSP audio-filter feature — section title and borders on dark surfaces. */
val NvhFilterAccent = NvhReportAccent

/** Emergence-detector feature accent (shares the "attention" amber of [NvhStatusWarn]). */
val NvhDetectorAccent = NvhStatusWarn

/** Notice banner (file rejected, truncation, GNSS state) background/border/text. */
val NvhNoticeContainer = Color(0xE6301B0F)
val NvhNoticeBorder = Color(0xFFF59E0B)
val NvhOnNotice = Color(0xFFFFE0B2)

/**
 * Order-trace palette shared by the screen and the PDF report [U7, plan 4.5].
 *
 * Stored as ARGB ints so `android.graphics.Paint` (PDF) and `Compose Color` (screen) can use
 * the identical value — the report and the display must never disagree about which colour
 * belongs to which tracked order. Hues are spaced for categorical discrimination, and every
 * entry clears 3:1 against the app's black canvas *and* 2:1 against the report's white page
 * (`PaletteContrastTest` enforces both; pure yellow fails the page test, hence the gold).
 */
val NvhOrderTraceArgb: List<Int> =
    listOf(
        0xFF00B0FF.toInt(), // azure
        0xFFFF6D00.toInt(), // orange
        0xFF00C853.toInt(), // green
        0xFFD500F9.toInt(), // violet
        0xFFD4A200.toInt(), // gold
        0xFF00BFA5.toInt(), // teal
        0xFFFF1744.toInt(), // red
    )

/** [NvhOrderTraceArgb] as Compose colours, in the same order. */
val NvhOrderTrace: List<Color> = NvhOrderTraceArgb.map { Color(it) }

/**
 * Severity ramp for an emergence (TTNR) level, in dB above the local noise floor.
 *
 * One definition for every surface that grades emergence — the 2D telemetry graph, the
 * canvas beacons, the emergence-report badges and the PDF [U7]. The thresholds are the
 * detector's own reporting steps, not decoration: changing them changes what an operator is
 * told is critical, so they live in exactly one place.
 */
fun nvhEmergenceColor(emergenceDb: Double): Color =
    when {
        emergenceDb >= EMERGENCE_CRITICAL_DB -> NvhEmergenceCritical
        emergenceDb >= EMERGENCE_HIGH_DB -> NvhEmergenceHigh
        emergenceDb >= EMERGENCE_MODERATE_DB -> NvhEmergenceModerate
        emergenceDb >= EMERGENCE_LOW_DB -> NvhEmergenceLow
        emergenceDb > 0.0 -> NvhEmergenceMarginal
        else -> NvhOnSurface
    }

const val EMERGENCE_CRITICAL_DB = 20.0
const val EMERGENCE_HIGH_DB = 15.0
const val EMERGENCE_MODERATE_DB = 10.0
const val EMERGENCE_LOW_DB = 5.0

val NvhEmergenceCritical = Color(0xFFD50000)
val NvhEmergenceHigh = Color(0xFFFF1744)
val NvhEmergenceModerate = Color(0xFFFF6D00)
val NvhEmergenceLow = Color(0xFFFFA726)
val NvhEmergenceMarginal = Color(0xFFFFD54F)
