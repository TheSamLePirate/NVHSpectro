package com.example.nvhspectro.data

/**
 * The single index/time mapping between parallel, uniformly-sampled timelines
 * (FFT frames ↔ telemetry samples ↔ playback position) [audit C17, plan 1.4].
 *
 * FFT-frame indices are NOT telemetry indices: a 5-minute WAV has ~12,900
 * frames but maybe 30 telemetry samples. Every cross-timeline lookup goes
 * through these two functions — never index one list with another's index.
 */
object TimelineMapper {

    /**
     * Map an index between two lists that span the same time range.
     * Exact identity when the sizes match (the live-mode 1:1 case).
     */
    fun mapIndex(index: Int, fromSize: Int, toSize: Int): Int {
        if (toSize <= 0) return 0
        if (fromSize <= 1) return 0
        val clamped = index.coerceIn(0, fromSize - 1)
        val mapped = Math.round(clamped.toDouble() / (fromSize - 1) * (toSize - 1)).toInt()
        return mapped.coerceIn(0, toSize - 1)
    }

    /** Index of the entry at time [posMs] in a list of [size] entries spanning [durationMs]. */
    fun timeToIndex(posMs: Long, durationMs: Long, size: Int): Int {
        if (size <= 0) return 0
        if (durationMs <= 0) return 0
        val ratio = (posMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return Math.round(ratio * (size - 1)).toInt().coerceIn(0, size - 1)
    }
}
