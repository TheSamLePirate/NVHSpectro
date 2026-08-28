package com.example.nvhspectro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.PlotGeometry

/**
 * Every plot margin and canvas text size, in dp/sp, converted once [U3, plan 4.2].
 *
 * The audit found these as raw pixel literals (`150f`, `60f`, `120f`, `40f`, `32f`, `26f`,
 * `22f`) repeated in two `pointerInput` blocks and the draw pass of each canvas. Physical
 * pixels mean the axis gutter is ~34 dp with ~9 sp text on an xxxhdpi phone and enormous on
 * mdpi — very plausibly the "layout bugs found on device" class this project already hit —
 * and duplicating them means touch handling can silently disagree with what is drawn.
 *
 * Canvas text also has to honour the user's font scale: `sp.toPx()` applies it, a raw pixel
 * size ignores it entirely, which is why the old canvases were unreadable at large font
 * scales while the rest of the UI grew [§12].
 */
class PlotDimens(
    density: Density,
    fontScale: Float,
) {
    // --- Spectrogram canvas -------------------------------------------------------------
    val spectroMarginLeft: Float
    val spectroMarginTop: Float
    val spectroMarginRight: Float
    val spectroMarginBottom: Float

    // --- Telemetry graph ----------------------------------------------------------------
    val graphMarginLeft: Float
    val graphMarginTop: Float
    val graphMarginRight: Float
    val graphMarginBottom: Float

    // --- Canvas text (font-scale aware) -------------------------------------------------
    val axisTextSize: Float
    val labelTextSize: Float
    val tagTextSize: Float
    val badgeTextSize: Float

    // --- Strokes and radii --------------------------------------------------------------
    val hairline: Float
    val traceStroke: Float
    val cursorStroke: Float
    val markerRadius: Float

    init {
        with(density) {
            spectroMarginLeft = 52.dp.toPx()
            spectroMarginTop = 20.dp.toPx()
            spectroMarginRight = 14.dp.toPx()
            spectroMarginBottom = 40.dp.toPx()

            graphMarginLeft = 62.dp.toPx()
            graphMarginTop = 8.dp.toPx()
            graphMarginRight = 10.dp.toPx()
            graphMarginBottom = 16.dp.toPx()

            axisTextSize = 9.sp.toPx()
            labelTextSize = 10.sp.toPx()
            tagTextSize = 9.sp.toPx()
            badgeTextSize = 10.sp.toPx()

            hairline = 1.dp.toPx()
            traceStroke = 1.5.dp.toPx()
            cursorStroke = 2.dp.toPx()
            markerRadius = 3.dp.toPx()
        }
    }
}

/**
 * The spectrogram's geometry for a given canvas size and view transform [U3, U4].
 *
 * Both `pointerInput` blocks and the draw pass call this, so there is exactly one definition
 * of where the plot is and how zoom/pan map data to pixels.
 */
fun PlotDimens.spectroGeometry(
    widthPx: Float,
    heightPx: Float,
    zoom: Float,
    pan: androidx.compose.ui.geometry.Offset,
): PlotGeometry =
    PlotGeometry(
        widthPx = widthPx,
        heightPx = heightPx,
        marginLeftPx = spectroMarginLeft,
        marginTopPx = spectroMarginTop,
        marginRightPx = spectroMarginRight,
        marginBottomPx = spectroMarginBottom,
        zoom = zoom,
        panXPx = pan.x,
        panYPx = pan.y,
    )

/** The telemetry graph's geometry (no zoom/pan: it always shows the whole window). */
fun PlotDimens.graphGeometry(
    widthPx: Float,
    heightPx: Float,
): PlotGeometry =
    PlotGeometry(
        widthPx = widthPx,
        heightPx = heightPx,
        marginLeftPx = graphMarginLeft,
        marginTopPx = graphMarginTop,
        marginRightPx = graphMarginRight,
        marginBottomPx = graphMarginBottom,
    )

/** The plot metrics for the current density and font scale; recomputed only when those change. */
@Composable
fun rememberPlotDimens(): PlotDimens {
    val density = LocalDensity.current
    return remember(density.density, density.fontScale) { PlotDimens(density, density.fontScale) }
}
