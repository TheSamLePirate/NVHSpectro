package com.example.nvhspectro

/** How a frame's capture timestamp was obtained [plan-gps GPS-1.2]. */
enum class AudioTimestampSource {
    /** Anchored on AudioRecord.getTimestamp(TIMEBASE_BOOTTIME) — the accurate path. */
    HARDWARE,

    /** Hardware timestamp unavailable: anchored on the read-completion clock — explicitly less precise. */
    ESTIMATED,
}

/**
 * [plan-gps GPS-1.2, GPS-03] One analysis window with its CAPTURE time.
 *
 * The speed estimate for a spectrum must be evaluated at the BOOTTIME instant
 * the sound was captured — `estimateAt(centerTimeNanos)` — never at the
 * instant the DSP got around to processing it: a backlogged DSP queue used to
 * silently pair a spectrum with a speed newer than the audio.
 *
 * Units and time bases [GPS-0.5]: times are BOOTTIME nanoseconds
 * (elapsedRealtimeNanos base); [pcm] is 16-bit mono at [sampleRateHz].
 *
 * Deliberately NOT a data class: [pcm] is a reused-content array and
 * structural equality over it would be wrong and expensive.
 */
class CapturedAudioFrame(
    val pcm: ShortArray,
    /** BOOTTIME of the window's first sample. */
    val firstSampleTimeNanos: Long,
    /** BOOTTIME of the window's center sample — the instant a spectrum "is at". */
    val centerTimeNanos: Long,
    val sampleRateHz: Int,
    /** Monotonic counter from capture start — pairs with the integrity counters [plan 2.6]. */
    val sequenceNumber: Long,
    val timestampSource: AudioTimestampSource,
)
