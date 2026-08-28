package com.example.nvhspectro

import com.example.nvhspectro.data.ManualOrderAnchor
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan 3.3, audit §13.4] The assisted manual tracing algorithm, extracted
 * pure: ridge following between user anchors, pinned user points, guide-line
 * fallback and jump clamping.
 */
class SmartPathTrackerTest {

    private val binCount = 200

    /** History whose spectral ridge sits at [ridge] (frame) with a 3-bin dome. */
    private fun ridgeHistory(frames: Int, ridge: (Int) -> Int): List<FloatArray> =
        List(frames) { f ->
            FloatArray(binCount) { b ->
                val d = abs(b - ridge(f))
                when {
                    d == 0 -> -20f
                    d == 1 -> -30f
                    else -> -90f
                }
            }
        }

    @Test
    fun path_followsTheSpectralRidgeBetweenAnchors() {
        // Ridge climbs one bin every 2 frames: 50 → 75 over 50 frames.
        val history = ridgeHistory(60) { f -> 50 + f / 2 }
        val points = listOf(ManualOrderAnchor(0, 50), ManualOrderAnchor(50, 75))
        val path = SmartPathTracker.compute(points, history)

        assertEquals("one anchor per frame in range", 51, path.size)
        // Mid-path must sit on the ridge (±2 bins after smoothing).
        val mid = path[25]
        assertEquals("contiguous frame indices", 25, mid.frameIndex)
        assertTrue("path must ride the ridge, got bin ${mid.binIndex}", abs(mid.binIndex - (50 + 25 / 2)) <= 2)
    }

    @Test
    fun userAnchors_stayPinned() {
        val history = ridgeHistory(30) { 100 }
        val points = listOf(ManualOrderAnchor(0, 90), ManualOrderAnchor(29, 110))
        val path = SmartPathTracker.compute(points, history)
        assertTrue(path.first().isUserPlaced)
        assertEquals(90, path.first().binIndex)
        assertTrue(path.last().isUserPlaced)
        assertEquals(110, path.last().binIndex)
    }

    @Test
    fun flatSpectrum_fallsBackToTheGuideLine() {
        val flat = List(21) { FloatArray(binCount) { -60f } }
        val points = listOf(ManualOrderAnchor(0, 40), ManualOrderAnchor(20, 60))
        val path = SmartPathTracker.compute(points, flat)
        // No local maxima anywhere: the path is the straight guide line.
        val mid = path[10]
        assertTrue("guide-line fallback, got ${mid.binIndex}", abs(mid.binIndex - 50) <= 1)
    }

    @Test
    fun degenerateInputs_returnEmptyOrThePointsThemselves() {
        assertTrue(SmartPathTracker.compute(emptyList(), ridgeHistory(5) { 10 }).isEmpty())
        assertTrue(SmartPathTracker.compute(listOf(ManualOrderAnchor(0, 10)), ridgeHistory(5) { 10 }).isEmpty())
        assertTrue(SmartPathTracker.compute(listOf(ManualOrderAnchor(3, 10), ManualOrderAnchor(3, 12)), ridgeHistory(5) { 10 }).size == 2)
    }
}
