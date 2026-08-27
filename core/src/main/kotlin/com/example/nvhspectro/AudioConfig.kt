package com.example.nvhspectro

/**
 * The single home of audio-format constants [audit C1, plan 1.1].
 *
 * This is the ONLY file in app/src/main where a literal sample rate may
 * appear — `ci/checks.sh` enforces it. Everything downstream of a loaded
 * file must use that file's own `LoadedWavData.sampleRate`; only the live
 * microphone pipeline uses [LIVE_SAMPLE_RATE_HZ].
 */
object AudioConfig {
    /** Sample rate of live microphone capture. */
    const val LIVE_SAMPLE_RATE_HZ = 44100

    /** FFT size used for full-file WAV/video analysis (fixed by design; see SettingsDialog). */
    const val WAV_FFT_SIZE = 2048

    /** Default FFT size for live capture (user-adjustable in settings). */
    const val DEFAULT_FFT_SIZE = 2048

    /**
     * [D7, plan 3.7] Display policy: spectrogram surfaces paint bins below
     * this as floor. The DATA is true — the old code destroyed sub-30 Hz
     * magnitudes inside the FFT itself.
     */
    const val DISPLAY_MIN_FREQ_HZ = 30.0
}
