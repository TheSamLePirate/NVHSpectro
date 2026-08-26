package com.example.nvhspectro

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * [C5, C7, plan 2.1] The ONE owner of live microphone capture.
 *
 * Settings and enablement changes flow through flatMapLatest: the previous
 * capture is cancelled (mic released via AudioRepository.awaitClose) before a
 * new one starts — the historical bug class where every settings change
 * stacked another producer/consumer pair is structurally impossible here.
 * Disabled (mode != LIVE, or user stop) means no capture flow at all: the mic
 * indicator goes off.
 *
 * A capture error (mic busy, init failure) is reported via [onCaptureError]
 * and completes only the inner flow — a later re-enable retries cleanly.
 */
class CaptureEngine(
    private val audioRepository: AudioRepository,
    private val onCaptureError: (String) -> Unit
) {
    private data class Settings(val fftSize: Int, val enabled: Boolean)

    private val settings = MutableStateFlow(Settings(AudioConfig.DEFAULT_FFT_SIZE, enabled = true))

    // [plan 2.6] Integrity counters: framesProduced-framesConsumed ≤ buffer size
    // proves no leak; captureRestarts counts flatMapLatest switchovers.
    val framesProduced = AtomicLong(0)
    val framesConsumed = AtomicLong(0)
    val captureRestarts = AtomicLong(0)

    val isEnabled: Boolean get() = settings.value.enabled

    fun setFftSize(fftSize: Int) = settings.update { it.copy(fftSize = fftSize) }

    fun setEnabled(enabled: Boolean) = settings.update { it.copy(enabled = enabled) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun frames(): Flow<ShortArray> = settings
        // StateFlow already conflates equal values - no distinctUntilChanged needed.
        .flatMapLatest { s ->
            if (!s.enabled) {
                emptyFlow()
            } else {
                captureRestarts.incrementAndGet()
                audioRepository.startAudioCapture(s.fftSize)
                    .onEach { framesProduced.incrementAndGet() }
                    .catch { e ->
                        // Inner-flow catch: the engine stays alive for a retry.
                        onCaptureError("🎙️ ${e.message ?: "Capture micro impossible"}")
                    }
            }
        }
        // Bounded backpressure: a stalled consumer drops the OLDEST frames
        // instead of growing without limit (the old channel was UNLIMITED).
        .buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .onEach { framesConsumed.incrementAndGet() }
}
