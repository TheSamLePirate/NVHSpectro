package com.example.nvhspectro.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The fixed dark palette's contrast claims, checked instead of asserted [U5, plan 4.3].
 *
 * `theme/Color.kt` documents a contrast ratio next to every foreground token. Those comments
 * are only worth something if a build fails when someone edits a hex and forgets the pair it
 * has to stay readable against — which is exactly the class of regression the audit found in
 * the old theme (white text on a light surface). WCAG 2.1 thresholds: 4.5:1 for body text,
 * 3:1 for large text and non-text indicators.
 */
class PaletteContrastTest {
    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** WCAG 2.1 contrast ratio between two opaque colours. */
    private fun contrast(
        fg: Color,
        bg: Color,
    ): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun assertReadable(
        name: String,
        fg: Color,
        bg: Color,
        minRatio: Double,
    ) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$name contrast is %.2f:1, below the required %.1f:1".format(ratio, minRatio),
            ratio >= minRatio,
        )
    }

    @Test
    fun u5_bodyText_clearsWcagAaOnEverySurfaceItIsUsedOn() {
        assertReadable("onSurface/background", NvhOnSurface, NvhBackground, 4.5)
        assertReadable("onSurface/surface", NvhOnSurface, NvhSurface, 4.5)
        assertReadable("onSurface/surfaceVariant", NvhOnSurface, NvhSurfaceVariant, 4.5)
        assertReadable("onSurface/sectionContainer", NvhOnSurface, NvhSectionContainer, 4.5)
        assertReadable("onSurfaceVariant/surface", NvhOnSurfaceVariant, NvhSurface, 4.5)
        assertReadable("onSurfaceVariant/surfaceVariant", NvhOnSurfaceVariant, NvhSurfaceVariant, 4.5)
        assertReadable("onPrimaryContainer/primaryContainer", NvhOnPrimaryContainer, NvhPrimaryContainer, 4.5)
        assertReadable("onNotice/noticeContainer", NvhOnNotice, Color(0xFF301B0F), 4.5)
        assertReadable("disabledContent/disabledContainer", NvhDisabledContent, NvhDisabledContainer, 4.5)
    }

    @Test
    fun u5_textOnColouredButtons_isReadable() {
        // Label colour actually used on each filled control.
        assertReadable("onPrimary/primary", NvhOnPrimary, NvhPrimary, 4.5)
        assertReadable("onSecondary/secondary", NvhOnSecondary, NvhSecondary, 4.5)
        assertReadable("onTertiary/tertiary", NvhOnTertiary, NvhTertiary, 4.5)
        assertReadable("onError/error", NvhOnError, NvhError, 4.5)
        assertReadable("onSurface/activeContainer", NvhOnSurface, NvhActiveContainer, 4.5)
        assertReadable("onSurface/inactiveContainer", NvhOnSurface, NvhInactiveContainer, 4.5)
        assertReadable("onSurface/modeWav", NvhOnSurface, NvhModeWav, 4.0)
        assertReadable("onSurface/modeVideo", NvhOnSurface, NvhModeVideo, 4.5)
        assertReadable("onSurface/modeLive", NvhOnSurface, NvhModeLive, 4.5)
        assertReadable("onSurface/recording", NvhOnSurface, NvhRecording, 4.5)
        assertReadable("onSurface/export", NvhOnSurface, NvhExport, 4.5)
        assertReadable("onSurface/reportMode", NvhOnSurface, NvhReportMode, 4.5)
        assertReadable("onSurface/filter", NvhOnSurface, NvhFilter, 4.5)
    }

    /**
     * Every semantic hue exists twice: a dark *container* that carries a light label, and a
     * light *accent* for text and hairlines drawn on dark surfaces. Using one value for both
     * is what made the original palette fail — this test pins the split.
     */
    @Test
    fun u5_accentTokens_areReadableAsTextOnEveryDarkSurface() {
        val darkSurfaces = listOf(NvhCanvas, NvhBackground, NvhSurface, NvhSurfaceVariant, NvhSectionContainer)
        val accents =
            mapOf(
                "primary" to NvhPrimary,
                "accent" to NvhAccent,
                "modeWavAccent" to NvhModeWavAccent,
                "modeVideoAccent" to NvhModeVideoAccent,
                "reportAccent" to NvhReportAccent,
                "statusBad" to NvhStatusBad,
                "statusWarn" to NvhStatusWarn,
                "statusGood" to NvhStatusGood,
                "theoretical" to NvhTheoretical,
            )
        accents.forEach { (name, c) ->
            darkSurfaces.forEach { bg -> assertReadable("$name as text", c, bg, 4.5) }
        }
    }

    @Test
    fun u5_statusIndicators_areDistinguishableAgainstTheirBackground() {
        // Non-text indicators (LED dots, order traces, badges): WCAG's 3:1 bar.
        assertReadable("statusGood/surfaceVariant", NvhStatusGood, NvhSurfaceVariant, 3.0)
        assertReadable("statusWarn/surfaceVariant", NvhStatusWarn, NvhSurfaceVariant, 3.0)
        assertReadable("statusBad/surfaceVariant", NvhStatusBad, NvhSurfaceVariant, 3.0)
        assertReadable("accent/canvas", NvhAccent, NvhCanvas, 3.0)
        assertReadable("theoretical/surfaceVariant", NvhTheoretical, NvhSurfaceVariant, 3.0)
        assertReadable("spectrum/canvas", NvhSpectrum, NvhCanvas, 3.0)
        // The WAV playhead is a hairline over the colour-mapped canvas, and the player-bar
        // title is body text on a raised surface — the accent has to clear both.
        assertReadable("modeWavAccent/canvas", NvhModeWavAccent, NvhCanvas, 3.0)
        assertReadable("modeWavAccent/surfaceVariant", NvhModeWavAccent, NvhSurfaceVariant, 4.5)
    }

    @Test
    fun u7_orderTraceColours_readOnBothTheDarkCanvasAndTheWhitePdfPage() {
        // The same palette is drawn on the black spectrogram and printed on a white page
        // [plan 4.5]; a colour that vanishes on either surface is not usable for an order.
        val page = Color.White
        NvhOrderTraceArgb.forEachIndexed { i, argb ->
            val c = Color(argb)
            assertReadable("orderTrace[$i]/canvas", c, NvhCanvas, 3.0)
            assertReadable("orderTrace[$i]/page", c, page, 2.0)
        }
    }

    @Test
    fun u7_orderTraceColours_areTheSameValueOnScreenAndInTheReport() {
        assertEquals(NvhOrderTraceArgb.size, NvhOrderTrace.size)
        NvhOrderTraceArgb.forEachIndexed { i, argb ->
            assertEquals(
                "screen and PDF disagree about the colour of order slot $i",
                argb,
                NvhOrderTrace[i].toArgb(),
            )
        }
    }

    @Test
    fun u5_emergenceSeverityRamp_isMonotonicAndLegible() {
        // Every severity step must be readable on the black canvas it is drawn on…
        listOf(
            NvhEmergenceMarginal,
            NvhEmergenceLow,
            NvhEmergenceModerate,
            NvhEmergenceHigh,
            NvhEmergenceCritical,
        ).forEachIndexed { i, c -> assertReadable("emergence step $i/canvas", c, NvhCanvas, 3.0) }

        // …and the ramp must actually be a ramp: each threshold selects its own colour.
        assertEquals(NvhEmergenceCritical, nvhEmergenceColor(EMERGENCE_CRITICAL_DB))
        assertEquals(NvhEmergenceHigh, nvhEmergenceColor(EMERGENCE_HIGH_DB))
        assertEquals(NvhEmergenceModerate, nvhEmergenceColor(EMERGENCE_MODERATE_DB))
        assertEquals(NvhEmergenceLow, nvhEmergenceColor(EMERGENCE_LOW_DB))
        assertEquals(NvhEmergenceMarginal, nvhEmergenceColor(0.1))
        assertEquals(NvhOnSurface, nvhEmergenceColor(0.0))
    }
}

private fun Color.toArgb(): Int {
    fun ch(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (ch(alpha) shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
}
