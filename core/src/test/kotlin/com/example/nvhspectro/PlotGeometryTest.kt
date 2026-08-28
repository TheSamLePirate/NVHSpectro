package com.example.nvhspectro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plot geometry and the zoom/pan transform [U3, U4, plan 4.2].
 *
 * U4 was a *disagreement* bug: the spectrogram bitmap was cropped by zoom while the overlays
 * drawn on top of it were placed in unzoomed coordinates, so playhead, beacons, tags and the
 * H1 curve slid off the image the moment a user pinched. These tests pin the single transform
 * both sides now use, and its inverse — the one the touch handler needs.
 */
class PlotGeometryTest {
    private fun geo(
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ) = PlotGeometry(
        widthPx = 1000f,
        heightPx = 600f,
        marginLeftPx = 100f,
        marginTopPx = 50f,
        marginRightPx = 50f,
        marginBottomPx = 100f,
        zoom = zoom,
        panXPx = panX,
        panYPx = panY,
    )

    @Test
    fun u3_thePlotArea_isTheCanvasMinusItsGutters() {
        val g = geo()
        assertEquals(850f, g.plotWidth, 1e-4f)
        assertEquals(450f, g.plotHeight, 1e-4f)
        assertEquals(100f, g.left, 1e-4f)
        assertEquals(950f, g.right, 1e-4f)
        assertEquals(50f, g.top, 1e-4f)
        assertEquals(500f, g.bottom, 1e-4f)
    }

    @Test
    fun u4_atZoomOne_fractionsMapAcrossTheWholePlot() {
        val g = geo()
        assertEquals(g.left, g.xForFraction(0f), 1e-4f)
        assertEquals(g.right, g.xForFraction(1f), 1e-4f)
        assertEquals(g.top, g.yForFraction(0f), 1e-4f)
        assertEquals(g.bottom, g.yForFraction(1f), 1e-4f)
    }

    @Test
    fun u4_theTransformAndItsInverse_roundTripAtAnyZoom() {
        listOf(geo(), geo(zoom = 3f, panX = -200f, panY = -120f), geo(zoom = 20f, panX = -1000f)).forEach { g ->
            listOf(0f, 0.13f, 0.5f, 0.87f, 1f).forEach { u ->
                assertEquals(u, g.fractionForX(g.xForFraction(u)), 1e-4f)
                assertEquals(u, g.fractionForY(g.yForFraction(u)), 1e-4f)
            }
        }
    }

    @Test
    fun u4_overlaysAndTheBitmapCrop_agreeAboutWhatIsVisible() {
        // This is the U4 bug in one assertion: the visible data range implied by the overlay
        // transform must be the range the bitmap crop shows.
        val g = geo(zoom = 4f, panX = -1275f) // -1275 = -(0.375 * 850 * 4): view starts at 0.375
        val visible = g.visibleFractionX()
        assertEquals(0.375f, visible.start, 1e-4f)
        assertEquals(0.625f, visible.endInclusive, 1e-4f)

        // An overlay at the left edge of the visible data lands on the left edge of the plot.
        assertEquals(g.left, g.xForFraction(visible.start), 1e-3f)
        assertEquals(g.right, g.xForFraction(visible.endInclusive), 1e-3f)
    }

    @Test
    fun u4_dataOutsideTheViewport_isReportedOutOfBounds() {
        val g = geo(zoom = 4f, panX = -1275f)
        assertFalse("data before the viewport must be clipped", g.containsX(g.xForFraction(0.1f)))
        assertTrue(g.containsX(g.xForFraction(0.5f)))
        assertFalse("data after the viewport must be clipped", g.containsX(g.xForFraction(0.9f)))
    }

    @Test
    fun u4_panIsClamped_soTheViewportNeverLeavesTheData() {
        val g = geo(zoom = 2f)
        // Trying to pan far right/down: clamped to 0 (top-left of the data).
        val (clampedX, clampedY) = g.clampPan(500f, 500f)
        assertEquals(0f, clampedX, 1e-4f)
        assertEquals(0f, clampedY, 1e-4f)
        // Trying to pan far left/up: clamped to exactly one plot-width/height of overscan.
        val (x, y) = g.clampPan(-99_999f, -99_999f)
        assertEquals(-g.plotWidth, x, 1e-4f)
        assertEquals(-g.plotHeight, y, 1e-4f)
    }

    @Test
    fun u4_atZoomOne_panIsPinnedToZero() {
        val g = geo()
        val (x, y) = g.clampPan(300f, -300f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(0f, y, 1e-4f)
    }

    @Test
    fun u4_zoomingAroundAPoint_keepsThatPointOverTheSameData() {
        val g = geo()
        val focusX = 525f // plot centre
        val focusY = 275f
        val dataBefore = g.fractionForX(focusX) to g.fractionForY(focusY)

        val zoomed = g.zoomedAround(zoomChange = 3f, focusX = focusX, focusY = focusY)
        assertEquals(3f, zoomed.zoom, 1e-4f)
        assertEquals(dataBefore.first, zoomed.fractionForX(focusX), 1e-3f)
        assertEquals(dataBefore.second, zoomed.fractionForY(focusY), 1e-3f)
    }

    @Test
    fun u4_zoomIsBounded_andNeverInverts() {
        val g = geo()
        assertEquals(PlotGeometry.MIN_ZOOM, g.zoomedAround(0.01f, 500f, 300f).zoom, 1e-4f)
        assertEquals(PlotGeometry.MAX_ZOOM, g.zoomedAround(1000f, 500f, 300f).zoom, 1e-4f)
    }

    @Test
    fun u4_resetReturnsTheFullView() {
        val reset = geo(zoom = 7f, panX = -400f, panY = -300f).reset()
        assertEquals(1f, reset.zoom, 1e-4f)
        assertEquals(0f, reset.panXPx, 1e-4f)
        assertEquals(0f, reset.panYPx, 1e-4f)
    }

    @Test
    fun u3_aCanvasTooSmallToPlotOn_isReportedUnusable() {
        assertFalse(geo().copy(widthPx = 120f).isUsable)
        assertFalse(geo().copy(heightPx = 130f).isUsable)
        assertTrue(geo().isUsable)
    }

    @Test
    fun u3_touchInTheGutter_isNotAPlotTouch() {
        val g = geo()
        assertFalse(g.containsPlot(10f, 300f)) // left gutter (axis labels)
        assertFalse(g.containsPlot(500f, 580f)) // bottom gutter (time axis)
        assertTrue(g.containsPlot(500f, 300f))
    }
}
