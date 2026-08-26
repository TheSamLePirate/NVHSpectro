package com.example.nvhspectro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

/**
 * [P1, P2, U2 — plan 3.5] Owns the spectrogram pixel pipeline.
 *
 * - Full-file renders are DOWNSAMPLED to at most [MAX_COLUMNS] columns — a
 *   5-minute file no longer allocates a ~13k-column bitmap (~52 MB) painted
 *   pixel-by-pixel on the main thread.
 * - Rendering runs on a background dispatcher (the caller dispatches); the
 *   result is double-buffered so every update returns a DIFFERENT Bitmap
 *   instance — Compose repaints on data change, replacing the historical
 *   mutate-and-hope-for-recomposition hack (the "black until first
 *   interaction" quirk, U2).
 * - One pixel buffer and two bitmaps for the producer's lifetime: zero
 *   per-frame allocation in the live path.
 */
class SpectrogramImageProducer(val width: Int, val height: Int) {

    private val pixels = IntArray(width * height) { AndroidColor.BLACK }
    private val buffers = arrayOf(
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    )
    private var current = 0

    /** Full-file render (WAV/report): columns sample the frame list uniformly. */
    @Synchronized
    fun renderFull(
        history: List<FloatArray>,
        minBin: Int,
        maxBin: Int,
        effectiveMin: Double,
        effectiveMax: Double,
        isTtnr: Boolean
    ): Bitmap {
        val numFrames = history.size
        val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
        for (x in 0 until width) {
            val frameIdx = (x * (numFrames - 1)) / maxOf(1, width - 1)
            val frame = if (frameIdx in history.indices) history[frameIdx] else EMPTY_FRAME
            for (y in 0 until height) {
                val binIndex = (maxBin - 1) - (y * (displayedBinCount - 1)) / maxOf(1, height - 1)
                val magnitude = if (binIndex in frame.indices) frame[binIndex].toDouble() else effectiveMin
                pixels[y * width + x] = colorFor(magnitude, effectiveMin, effectiveMax, isTtnr)
            }
        }
        return publish()
    }

    /** Live render: scroll one column left, paint the newest frame at the right edge. */
    @Synchronized
    fun appendLatest(
        latestFrame: FloatArray,
        minBin: Int,
        maxBin: Int,
        effectiveMin: Double,
        effectiveMax: Double,
        isTtnr: Boolean
    ): Bitmap {
        val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
        for (y in 0 until height) {
            System.arraycopy(pixels, y * width + 1, pixels, y * width, width - 1)
            val binIndex = (maxBin - 1) - (y * (displayedBinCount - 1)) / maxOf(1, height - 1)
            val magnitude = if (binIndex in latestFrame.indices) latestFrame[binIndex].toDouble() else effectiveMin
            pixels[y * width + (width - 1)] = colorFor(magnitude, effectiveMin, effectiveMax, isTtnr)
        }
        return publish()
    }

    private fun publish(): Bitmap {
        current = 1 - current
        val bitmap = buffers[current]
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    companion object {
        /**
         * Column budget for full-file renders: ~4× a 1080 px screen so report
         * zoom (max 20×) keeps useful native resolution, at ~1/3 the historical
         * full-width bitmap cost.
         */
        const val MAX_COLUMNS = 4096

        private val EMPTY_FRAME = FloatArray(0)

        fun columnsFor(frameCount: Int): Int = minOf(frameCount, MAX_COLUMNS).coerceAtLeast(1)

        fun colorFor(magnitude: Double, effectiveMin: Double, effectiveMax: Double, isTtnr: Boolean): Int {
            if (isTtnr && magnitude < 1.0) return AndroidColor.BLACK
            val rawNormalized = ((magnitude - effectiveMin) / (effectiveMax - effectiveMin)).coerceIn(0.0, 1.0).toFloat()
            val normalized = if (isTtnr && rawNormalized > 0f) {
                Math.pow(rawNormalized.toDouble(), 0.65).toFloat()
            } else {
                rawNormalized
            }
            return getJetColorInt(normalized)
        }
    }
}
