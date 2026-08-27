package com.example.nvhspectro

/**
 * The one description of where a plot's data area is, and how zoom/pan map data to pixels
 * [U3, U4, plan 4.2].
 *
 * Two audit findings share this object as their fix:
 *
 *  - **U3** — margins and text sizes were raw pixel literals (`150f`, `60f`, `32f`),
 *    duplicated across two `pointerInput` blocks and the draw pass. On an xxxhdpi phone the
 *    axis gutter measured ~34 dp with ~9 sp text; on mdpi it was enormous. The caller now
 *    converts dp/sp ONCE via `Density` and hands the pixel values here, so touch handling and
 *    drawing cannot drift apart — they read the same object.
 *  - **U4** — pinch-zoom cropped the spectrogram bitmap, but the playhead, emergence beacons,
 *    harmonic tags and the H1 overlay were positioned in *unzoomed* plot coordinates, so
 *    every overlay detached from the image the moment the user zoomed. Overlays now place
 *    themselves through [xForFraction]/[yForFraction], which apply the same transform the
 *    bitmap crop does, and [containsPlot] clips what falls outside the viewport.
 *
 * Coordinates: a *fraction* is a position in the DATA, 0..1 (u across time/frames, v down
 * from the top of the frequency band). Pixels are canvas coordinates.
 *
 * Pure Kotlin: this is the geometry, not the rendering, so it is unit-testable.
 */
data class PlotGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val marginLeftPx: Float,
    val marginTopPx: Float,
    val marginRightPx: Float,
    val marginBottomPx: Float,
    val zoom: Float = 1f,
    val panXPx: Float = 0f,
    val panYPx: Float = 0f,
) {
    val left: Float get() = marginLeftPx
    val top: Float get() = marginTopPx
    val plotWidth: Float get() = (widthPx - marginLeftPx - marginRightPx).coerceAtLeast(0f)
    val plotHeight: Float get() = (heightPx - marginTopPx - marginBottomPx).coerceAtLeast(0f)
    val right: Float get() = left + plotWidth
    val bottom: Float get() = top + plotHeight

    /** True when the plot area is big enough to be drawn on and touched meaningfully. */
    val isUsable: Boolean get() = plotWidth > MIN_USABLE_PX && plotHeight > MIN_USABLE_PX

    /** Data fraction (0..1 across the full data) -> canvas x. */
    fun xForFraction(u: Float): Float = left + (u * plotWidth * zoom + panXPx)

    /** Data fraction (0 = top of the band, 1 = bottom) -> canvas y. */
    fun yForFraction(v: Float): Float = top + (v * plotHeight * zoom + panYPx)

    /** Canvas x -> data fraction. Inverse of [xForFraction]. */
    fun fractionForX(x: Float): Float = if (plotWidth <= 0f || zoom <= 0f) 0f else (x - left - panXPx) / (plotWidth * zoom)

    /** Canvas y -> data fraction. Inverse of [yForFraction]. */
    fun fractionForY(y: Float): Float = if (plotHeight <= 0f || zoom <= 0f) 0f else (y - top - panYPx) / (plotHeight * zoom)

    /** The data fractions currently visible horizontally — what the bitmap crop shows. */
    fun visibleFractionX(): ClosedFloatingPointRange<Float> = fractionForX(left)..fractionForX(right)

    /** The data fractions currently visible vertically. */
    fun visibleFractionY(): ClosedFloatingPointRange<Float> = fractionForY(top)..fractionForY(bottom)

    /** Is this canvas point inside the data area (not in the axis gutters)? */
    fun containsPlot(
        x: Float,
        y: Float,
    ): Boolean = x in left..right && y in top..bottom

    /** Is this canvas x inside the data area horizontally (for full-height overlays)? */
    fun containsX(x: Float): Boolean = x in left..right

    /** Is this canvas y inside the data area vertically (for full-width overlays)? */
    fun containsY(y: Float): Boolean = y in top..bottom

    /**
     * Clamps a candidate pan so the viewport can never leave the data.
     *
     * At zoom 1 the only valid pan is 0: the plot exactly fills its area, and letting it
     * slide would show blank space beside a measurement.
     */
    fun clampPan(
        candidateX: Float,
        candidateY: Float,
    ): Pair<Float, Float> {
        val maxPanX = 0f
        val minPanX = -(plotWidth * zoom - plotWidth)
        val maxPanY = 0f
        val minPanY = -(plotHeight * zoom - plotHeight)
        return candidateX.coerceIn(minPanX, maxPanX) to candidateY.coerceIn(minPanY, maxPanY)
    }

    /**
     * Zoom around a focal canvas point, keeping the data under that point in place.
     *
     * Returns the new geometry with the pan already clamped.
     */
    fun zoomedAround(
        zoomChange: Float,
        focusX: Float,
        focusY: Float,
        panChangeX: Float = 0f,
        panChangeY: Float = 0f,
    ): PlotGeometry {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val effectiveChange = if (zoom == 0f) 1f else newZoom / zoom
        val fx = focusX - left
        val fy = focusY - top
        val candidateX = panXPx * effectiveChange + fx * (1 - effectiveChange) + panChangeX
        val candidateY = panYPx * effectiveChange + fy * (1 - effectiveChange) + panChangeY
        val zoomed = copy(zoom = newZoom)
        val (px, py) = zoomed.clampPan(candidateX, candidateY)
        return zoomed.copy(panXPx = px, panYPx = py)
    }

    /** Back to the unzoomed, unpanned view. */
    fun reset(): PlotGeometry = copy(zoom = 1f, panXPx = 0f, panYPx = 0f)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 20f
        private const val MIN_USABLE_PX = 50f
    }
}
