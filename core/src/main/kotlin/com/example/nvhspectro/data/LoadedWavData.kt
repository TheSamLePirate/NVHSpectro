package com.example.nvhspectro.data

import com.example.nvhspectro.TelemetryData

/** The analyzed audio of a loaded WAV/video source, in its OWN sample rate [audit C1]. */
data class LoadedWavData(
    val pcmSamples: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
    val telemetryList: List<TelemetryData> = emptyList(),
    /** Per-sample audio BOOTTIME from a v3 sidecar [GPS-4.3]; null on older ones. */
    val telemetryAudioTimesNanos: List<Long>? = null,
)

/**
 * Why a WAV import failed [audit C2, plan 1.2; §12, plan 4.4].
 *
 * The reader reports *what* happened; the UI decides how to say it. The messages used to be
 * French literals built inside the RIFF walker, which made them impossible to localise and
 * impossible to assert on without string matching.
 */
enum class WavReadError {
    FILE_NOT_FOUND,
    UNREADABLE,
    INACCESSIBLE,
    TOO_SHORT,
    NOT_RIFF,
    FMT_TRUNCATED,
    FMT_UNREADABLE,
    FMT_MISSING,
    DATA_MISSING,
    FORMAT_UNSUPPORTED,
    BITS_UNSUPPORTED,
    CHANNELS_UNSUPPORTED,
    SAMPLE_RATE_INVALID,
    NO_DECODABLE_DATA,
}

/** Typed outcome of a WAV import [audit C2, plan 1.2]. */
sealed class WavReadResult {
    data class Success(
        val data: LoadedWavData,
        /** True when the file is longer than the analysis cap and was cut there. */
        val truncatedToCap: Boolean,
    ) : WavReadResult()

    /**
     * A file the app can read but deliberately refuses to analyse (24-bit, float, 5.1…).
     * [detail] is the offending value (bit depth, channel count) for the message.
     */
    data class Unsupported(
        val reason: WavReadError,
        val detail: String = "",
    ) : WavReadResult()

    /** A file the app could not read at all. */
    data class Error(
        val reason: WavReadError,
        val detail: String = "",
    ) : WavReadResult()
}
