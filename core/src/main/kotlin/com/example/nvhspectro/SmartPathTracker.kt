package com.example.nvhspectro

import com.example.nvhspectro.data.ManualOrderAnchor
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sign

/**
 * [plan 3.3, audit §13.4] Assisted manual order tracing — pure and JVM-tested.
 * From the user's anchor points, follows the spectral ridge between them:
 * guide-line interpolation, local-max scoring with jump/guide penalties,
 * sub-bin parabolic refinement, jump clamping, and a 5-point moving average.
 */
object SmartPathTracker {

    private const val SEARCH_RADIUS_BINS = 20
    private const val JUMP_PENALTY_PER_BIN = 1.5f
    private const val GUIDE_PENALTY_PER_BIN = 0.5f
    private const val MAX_JUMP_BINS = 15f
    private const val SMOOTHING_HALF_WINDOW = 2

    fun compute(points: List<ManualOrderAnchor>, history: List<FloatArray>): List<ManualOrderAnchor> {
        if (points.size < 2 || history.isEmpty()) return emptyList()

        val startFrame = points.first().frameIndex.coerceIn(0, history.size - 1)
        val endFrame = points.last().frameIndex.coerceIn(0, history.size - 1)
        if (startFrame >= endFrame) return points

        val numFrames = endFrame - startFrame + 1
        val binCount = history[startFrame].size

        // Guide line: strict linear interpolation between user anchors (the SLOPE prior).
        fun expectedBinF(globalFrame: Int): Float {
            if (globalFrame <= points.first().frameIndex) return points.first().binIndex.toFloat()
            if (globalFrame >= points.last().frameIndex) return points.last().binIndex.toFloat()
            for (i in 0 until points.size - 1) {
                if (globalFrame >= points[i].frameIndex && globalFrame <= points[i + 1].frameIndex) {
                    val f1 = points[i].frameIndex
                    val b1 = points[i].binIndex.toFloat()
                    val f2 = points[i + 1].frameIndex
                    val b2 = points[i + 1].binIndex.toFloat()
                    if (f1 == f2) return b1
                    return b1 + (globalFrame - f1).toFloat() / (f2 - f1).toFloat() * (b2 - b1)
                }
            }
            return points.last().binIndex.toFloat()
        }

        val dbEnergies = Array(numFrames) { f ->
            val spectrum = history[startFrame + f]
            FloatArray(binCount) { b -> spectrum[b] }
        }

        val rawPath = mutableListOf<ManualOrderAnchor>()
        var prevTrackedBinF = expectedBinF(startFrame)

        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val userPoint = points.firstOrNull { it.frameIndex == globalFrame }
            if (userPoint != null) {
                rawPath.add(ManualOrderAnchor(globalFrame, userPoint.binIndex, isUserPlaced = true, exactBinF = userPoint.binIndex.toFloat()))
                prevTrackedBinF = userPoint.binIndex.toFloat()
                continue
            }

            val expected = expectedBinF(globalFrame)
            val centerSearchInt = round(expected).toInt()
            val minBin = (centerSearchInt - SEARCH_RADIUS_BINS).coerceAtLeast(0)
            val maxBin = (centerSearchInt + SEARCH_RADIUS_BINS).coerceAtMost(binCount - 1)

            var bestBin = centerSearchInt
            var maxScore = -Float.MAX_VALUE

            for (b in minBin..maxBin) {
                val e = dbEnergies[f][b]
                // Only local maxima: never slide down the flank of a neighboring harmonic.
                val isLocalMax = if (b > 0 && b < binCount - 1) {
                    e > dbEnergies[f][b - 1] && e > dbEnergies[f][b + 1]
                } else {
                    true
                }
                if (isLocalMax) {
                    val score = e - JUMP_PENALTY_PER_BIN * abs(b - prevTrackedBinF) -
                        GUIDE_PENALTY_PER_BIN * abs(b - expected)
                    if (score > maxScore) {
                        maxScore = score
                        bestBin = b
                    }
                }
            }

            // Flat spectrum (no local max found): fall back to the guide line.
            if (maxScore == -Float.MAX_VALUE) {
                bestBin = round(expected).toInt()
            }

            var exactBinF = bestBin.toFloat()
            if (bestBin > 0 && bestBin < binCount - 1 && maxScore != -Float.MAX_VALUE) {
                val y1 = dbEnergies[f][bestBin - 1]
                val y2 = dbEnergies[f][bestBin]
                val y3 = dbEnergies[f][bestBin + 1]
                val denom = 2f * (y1 - 2f * y2 + y3)
                if (denom != 0f) {
                    exactBinF = bestBin + ((y1 - y3) / denom).coerceIn(-0.5f, 0.5f)
                }
            }

            // Clamp truly aberrant jumps.
            if (abs(exactBinF - prevTrackedBinF) > MAX_JUMP_BINS) {
                exactBinF = prevTrackedBinF + sign(exactBinF - prevTrackedBinF) * MAX_JUMP_BINS
                bestBin = round(exactBinF).toInt()
            }

            rawPath.add(ManualOrderAnchor(globalFrame, bestBin, isUserPlaced = false, exactBinF = exactBinF))
            prevTrackedBinF = exactBinF
        }

        // Moving average (user anchors stay pinned).
        val smoothedPath = mutableListOf<ManualOrderAnchor>()
        for (i in rawPath.indices) {
            val anchor = rawPath[i]
            if (anchor.isUserPlaced) {
                smoothedPath.add(anchor)
                continue
            }
            var sumBinF = 0f
            var count = 0
            for (j in -SMOOTHING_HALF_WINDOW..SMOOTHING_HALF_WINDOW) {
                val idx = i + j
                if (idx in rawPath.indices) {
                    sumBinF += rawPath[idx].exactBinF
                    count++
                }
            }
            val avgBinF = sumBinF / count
            smoothedPath.add(ManualOrderAnchor(anchor.frameIndex, round(avgBinF).toInt(), false, avgBinF))
        }
        return smoothedPath
    }
}
