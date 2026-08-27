package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.SystemClock
import com.example.nvhspectro.data.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

/** Raised when the microphone cannot be opened or keeps failing [C9] — surfaced to the UI, never a crash. */
class AudioCaptureException(
    message: String,
) : Exception(message)

class AudioRepository(
    context: Context,
) {
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

    /**
     * [plan-gps GPS-1.2] Emits [CapturedAudioFrame]s carrying the BOOTTIME of
     * each window's first and center sample, anchored on
     * `AudioRecord.getTimestamp(TIMEBASE_BOOTTIME)` (refreshed periodically);
     * when the hardware timestamp is unavailable the anchor falls back to the
     * read-completion clock and frames are marked [AudioTimestampSource.ESTIMATED]
     * (logged once). The speed chain evaluates its estimate at
     * `centerTimeNanos` [GPS-03].
     */
    @SuppressLint("MissingPermission")
    fun startAudioCapture(fftSize: Int = AudioConfig.DEFAULT_FFT_SIZE): Flow<CapturedAudioFrame> =
        callbackFlow {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val recordBufferSize = max(minBufferSize, fftSize * 2)

            // [C9] Validated mic acquisition: a busy mic or failed init used to
            // throw straight through the coroutine scope and crash the app.
            openValidatedRecord(recordBufferSize).fold(
                onSuccess = { audioRecord = it },
                onFailure = { close(it) },
            )

            // Fenêtre glissante avec recouvrement 50 %
            val stepSize = fftSize / 2
            val readBuffer = ShortArray(stepSize)
            val slidingWindow = ShortArray(fftSize)
            var consecutiveErrors = 0

            // [GPS-1.2] Frame-position ↔ BOOTTIME anchoring.
            val clock = AudioFrameClock(sampleRate)
            val timestamp = AudioTimestamp()
            var totalFramesRead = 0L
            var sequence = 0L
            var timestampSource = AudioTimestampSource.ESTIMATED
            var readsSinceAnchor = ANCHOR_REFRESH_READS // force an attempt on the first read

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                if (!fillStepCompletely(readBuffer, stepSize)) {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_READ_ERRORS) {
                        close(AudioCaptureException(appContext.getString(R.string.notice_mic_read_errors)))
                        break
                    }
                    Thread.sleep(READ_ERROR_BACKOFF_MS) // [C9] backoff instead of hot-spinning (on IO dispatcher)
                    continue
                }
                consecutiveErrors = 0
                totalFramesRead += stepSize

                val attemptHardwareAnchor = ++readsSinceAnchor >= ANCHOR_REFRESH_READS
                if (attemptHardwareAnchor) readsSinceAnchor = 0
                timestampSource =
                    maintainClockAnchor(clock, timestamp, totalFramesRead, attemptHardwareAnchor, timestampSource)

                System.arraycopy(slidingWindow, stepSize, slidingWindow, 0, fftSize - stepSize)
                System.arraycopy(readBuffer, 0, slidingWindow, fftSize - stepSize, stepSize)
                val firstSampleIndex = totalFramesRead - fftSize
                trySend(
                    CapturedAudioFrame(
                        pcm = slidingWindow.clone(),
                        firstSampleTimeNanos = clock.frameTimeNanos(firstSampleIndex),
                        centerTimeNanos = clock.frameTimeNanos(firstSampleIndex + fftSize / 2),
                        sampleRateHz = sampleRate,
                        sequenceNumber = sequence++,
                        timestampSource = timestampSource,
                    ),
                )
            }

            awaitClose {
                stopAudioCapture()
            }
        }.flowOn(Dispatchers.IO)

    /** [C9] Fill the step completely — a short read used to leave stale samples in the window tail. */
    private fun fillStepCompletely(
        readBuffer: ShortArray,
        stepSize: Int,
    ): Boolean {
        var filled = 0
        while (filled < stepSize) {
            val r = audioRecord?.read(readBuffer, filled, stepSize - filled) ?: -1
            if (r <= 0) return false
            filled += r
        }
        return true
    }

    /** [C9] Every acquisition step validated; failures become typed [AudioCaptureException]s. */
    @SuppressLint("MissingPermission")
    private fun openValidatedRecord(recordBufferSize: Int): Result<AudioRecord> =
        try {
            val record = AudioRecord(captureSource, sampleRate, channelConfig, audioFormat, recordBufferSize)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                Result.failure(AudioCaptureException(appContext.getString(R.string.notice_mic_init_failed)))
            } else {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    record.release()
                    Result.failure(AudioCaptureException(appContext.getString(R.string.notice_mic_busy)))
                } else {
                    Result.success(record)
                }
            }
        } catch (e: Exception) {
            Result.failure(
                AudioCaptureException(
                    appContext.getString(
                        R.string.notice_mic_capture_failed,
                        e.message ?: e.javaClass.simpleName,
                    ),
                ),
            )
        }

    /**
     * [GPS-1.2] Keep the frame clock anchored: prefer the hardware BOOTTIME
     * timestamp (attempted on schedule; never downgraded once obtained); fall
     * back to "last sample of this read ≈ now" — explicitly less precise,
     * logged once.
     */
    private fun maintainClockAnchor(
        clock: AudioFrameClock,
        timestamp: AudioTimestamp,
        totalFramesRead: Long,
        attemptHardware: Boolean,
        currentSource: AudioTimestampSource,
    ): AudioTimestampSource {
        var source = currentSource
        if (attemptHardware &&
            audioRecord?.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS
        ) {
            clock.setAnchor(timestamp.framePosition, timestamp.nanoTime)
            source = AudioTimestampSource.HARDWARE
        }
        if (!clock.hasAnchor || source == AudioTimestampSource.ESTIMATED) {
            clock.setAnchor(totalFramesRead, SystemClock.elapsedRealtimeNanos())
            if (!estimatedClockLogged) {
                // [V3] Also into the local diagnostic log: a degraded audio clock changes how
                // speed is paired with sound, so a later report must be able to show it.
                DiagnosticLog.w(TAG, "AudioTimestamp unavailable — using ESTIMATED audio clock")
                estimatedClockLogged = true
            }
        }
        return source
    }

    private var estimatedClockLogged = false

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
        const val TAG = "AudioRepository"
        const val MAX_READ_ERRORS = 25
        const val READ_ERROR_BACKOFF_MS = 40L

        /** Re-anchor about every ~0.7 s at the default FFT size — tracks clock drift cheaply. */
        const val ANCHOR_REFRESH_READS = 32
    }
}
