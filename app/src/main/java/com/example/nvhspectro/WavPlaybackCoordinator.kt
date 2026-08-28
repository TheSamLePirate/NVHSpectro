package com.example.nvhspectro

import com.example.nvhspectro.data.LoadedWavData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [plan 3.3] Drives the analyzer's playback position: play/pause/seek state,
 * the per-frame position poll that feeds the spectrum cursor, and the
 * analyzed-end stop [C3]. The MediaPlayer itself is owned by
 * [PlaybackController]; the loaded data lives in the session.
 */
class WavPlaybackCoordinator(
    private val scope: CoroutineScope,
    private val playback: PlaybackController,
    private val loadedData: () -> LoadedWavData?,
    private val onFrameAt: (Long) -> Unit
) {
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var pollJob: Job? = null

    /** Wire to [PlaybackController.onCompletion]. */
    fun onSourceCompleted() {
        _isPlaying.value = false
        loadedData()?.let { _positionMs.value = it.durationMs }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else start()
    }

    private fun start() {
        val data = loadedData() ?: return
        if (_isPlaying.value) return
        _isPlaying.value = true

        if (_positionMs.value >= data.durationMs) {
            _positionMs.value = 0L
            playback.seekTo(0)
        } else {
            playback.seekTo(_positionMs.value.toInt())
        }
        playback.play()

        pollJob?.cancel()
        pollJob = scope.launch {
            val stepSize = AudioConfig.WAV_FFT_SIZE / 2
            val stepMs = ((stepSize.toDouble() / data.sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(15L)

            while (_isPlaying.value && _positionMs.value < data.durationMs) {
                _positionMs.value = playback.currentPositionMs.toLong().coerceIn(0L, data.durationMs)
                onFrameAt(_positionMs.value)
                delay(stepMs)
            }

            if (_positionMs.value >= data.durationMs) {
                _isPlaying.value = false
                _positionMs.value = data.durationMs
                // [C3] Stop at the analyzed end instead of playing unanalyzed audio.
                playback.pause()
            }
        }
    }

    private fun pause() {
        _isPlaying.value = false
        pollJob?.cancel()
        pollJob = null
        playback.pause()
    }

    /** Pause and rewind to 0. The player stays prepared [L6]. */
    fun stopAndRewind() {
        pause()
        _positionMs.value = 0L
        playback.seekTo(0)
    }

    /** Position bookkeeping only — used when a fresh source was just loaded. */
    fun resetPosition() {
        _positionMs.value = 0L
    }

    fun seekTo(posMs: Long) {
        val data = loadedData() ?: return
        val clamped = posMs.coerceIn(0L, data.durationMs)
        _positionMs.value = clamped
        playback.seekTo(clamped.toInt())
        onFrameAt(clamped)
    }

    fun stepSeconds(offsetSec: Int) {
        val data = loadedData() ?: return
        seekTo((_positionMs.value + offsetSec * 1000L).coerceIn(0L, data.durationMs))
    }
}
