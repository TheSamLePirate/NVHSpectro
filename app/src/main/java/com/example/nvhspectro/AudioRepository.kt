package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** Raised when the microphone cannot be opened or keeps failing [C9] — surfaced to the UI, never a crash. */
class AudioCaptureException(message: String) : Exception(message)

class AudioRepository(context: Context) {
    private val appContext = context.applicationContext
    private var audioRecord: AudioRecord? = null
    private val sampleRate = AudioConfig.LIVE_SAMPLE_RATE_HZ
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * [C8] Measurement-grade capture: UNPROCESSED (no AGC/noise-suppression/EQ)
     * when the device advertises support, else VOICE_RECOGNITION — the
     * least-processed fallback. The generic MIC source applies device-tuned
     * processing that makes absolute dBFS values unstable. The active source is
     * recorded into export metadata so reports state which path produced them.
     */
    val captureSourceLabel: String
    private val captureSource: Int

    init {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessedSupported =
            am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        if (unprocessedSupported) {
            captureSource = MediaRecorder.AudioSource.UNPROCESSED
            captureSourceLabel = "UNPROCESSED"
        } else {
            captureSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
            captureSourceLabel = "VOICE_RECOGNITION"
        }
    }

    @SuppressLint("MissingPermission")
    fun startAudioCapture(fftSize: Int = AudioConfig.DEFAULT_FFT_SIZE): Flow<ShortArray> =
        callbackFlow {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val recordBufferSize = max(minBufferSize, fftSize * 2)

            // [C9] Validate every step of mic acquisition: a busy mic or failed init
            // used to throw IllegalStateException straight through the coroutine
            // scope and crash the app.
            try {
                val record = AudioRecord(captureSource, sampleRate, channelConfig, audioFormat, recordBufferSize)
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    close(AudioCaptureException("Micro indisponible (initialisation échouée)"))
                } else {
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        record.release()
                        close(AudioCaptureException("Micro occupé par une autre application"))
                    } else {
                        audioRecord = record
                    }
                }
            } catch (e: Exception) {
                close(AudioCaptureException("Capture micro impossible : ${e.message ?: e.javaClass.simpleName}"))
            }

            // Fenêtre glissante avec recouvrement 50 %
            val stepSize = fftSize / 2
            val readBuffer = ShortArray(stepSize)
            val slidingWindow = ShortArray(fftSize)
            var consecutiveErrors = 0

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                // [C9] Fill the step completely — a short read used to leave stale
                // samples in the window tail.
                var filled = 0
                var readFailed = false
                while (filled < stepSize) {
                    val r = audioRecord?.read(readBuffer, filled, stepSize - filled) ?: -1
                    if (r <= 0) {
                        readFailed = true
                        break
                    }
                    filled += r
                }

                if (readFailed) {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_READ_ERRORS) {
                        close(AudioCaptureException("Erreurs de lecture micro répétées — capture arrêtée"))
                        break
                    }
                    Thread.sleep(READ_ERROR_BACKOFF_MS) // [C9] backoff instead of hot-spinning (on IO dispatcher)
                    continue
                }
                consecutiveErrors = 0

                System.arraycopy(slidingWindow, stepSize, slidingWindow, 0, fftSize - stepSize)
                System.arraycopy(readBuffer, 0, slidingWindow, fftSize - stepSize, stepSize)
                trySend(slidingWindow.clone())
            }

            awaitClose {
                stopAudioCapture()
            }
        }.flowOn(Dispatchers.IO)

    fun stopAudioCapture() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // stop() on an already-stopped record is harmless
        }
        audioRecord?.release()
        audioRecord = null
    }

    private companion object {
        const val MAX_READ_ERRORS = 25
        const val READ_ERROR_BACKOFF_MS = 40L
    }
}
