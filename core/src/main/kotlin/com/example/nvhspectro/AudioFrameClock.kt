package com.example.nvhspectro

/**
 * [plan-gps GPS-1.2, GPS-03] Maps audio frame indices to BOOTTIME nanoseconds
 * from a (framePosition, nanoTime) anchor — the relation
 * `AudioRecord.getTimestamp` exposes.
 *
 * Guarantees monotonic non-decreasing output across calls even when a fresh
 * anchor steps slightly backward (hardware timestamps jitter): a regression
 * is clamped to the last returned value instead of ever going back in time.
 *
 * Units and time bases [GPS-0.5]: frame indices are sample frames since
 * capture start; times are BOOTTIME nanoseconds. Pure Kotlin, single-threaded
 * use (the capture loop owns it).
 */
class AudioFrameClock(
    private val sampleRateHz: Int,
) {
    private var anchorFrame = 0L
    private var anchorNanos = 0L
    private var anchored = false
    private var lastReturnedNanos = Long.MIN_VALUE

    val hasAnchor: Boolean get() = anchored

    /** Install or refresh the anchor: frame [framePosition] was captured at [nanoTime]. */
    fun setAnchor(
        framePosition: Long,
        nanoTime: Long,
    ) {
        anchorFrame = framePosition
        anchorNanos = nanoTime
        anchored = true
    }

    /**
     * BOOTTIME of sample frame [frameIndex], linear from the anchor at the
     * nominal rate. Callers must query indices in capture order — the
     * monotonic clamp is applied across successive calls.
     */
    fun frameTimeNanos(frameIndex: Long): Long {
        check(anchored) { "AudioFrameClock has no anchor" }
        val raw = anchorNanos + (frameIndex - anchorFrame) * NANOS_PER_SECOND / sampleRateHz
        val out = maxOf(raw, lastReturnedNanos)
        lastReturnedNanos = out
        return out
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
