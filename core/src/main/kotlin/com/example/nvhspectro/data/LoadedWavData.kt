package com.example.nvhspectro.data

import com.example.nvhspectro.TelemetryData

/** The analyzed audio of a loaded WAV/video source, in its OWN sample rate [audit C1]. */
data class LoadedWavData(
    val pcmSamples: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
    val telemetryList: List<TelemetryData> = emptyList()
)

/** Typed outcome of a WAV import [audit C2, plan 1.2] — failures carry a user-visible message. */
sealed class WavReadResult {
    data class Success(
        val data: LoadedWavData,
        /** True when the file is longer than the analysis cap and was cut there. */
        val truncatedToCap: Boolean
    ) : WavReadResult()

    data class Unsupported(val message: String) : WavReadResult()

    data class Error(val message: String) : WavReadResult()
}
