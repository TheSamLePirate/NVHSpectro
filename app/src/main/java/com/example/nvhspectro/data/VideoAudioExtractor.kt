package com.example.nvhspectro.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.nvhspectro.AudioConfig
import java.nio.ByteOrder

/**
 * Decodes a video's audio track to mono 16-bit PCM for analysis [C12, plan 4.8].
 *
 * Four defects the audit found in the previous implementation are fixed here:
 *  - the decode loop exited as soon as the *input* side queued EOS, dropping every output
 *    buffer still in flight — the last fraction of a second of audio was silently lost;
 *  - PCM accumulated in an `ArrayList<Short>`, i.e. ~13 M boxed objects for a 5-minute file
 *    (hundreds of MB of object overhead and GC storms on mid-range devices);
 *  - `INFO_OUTPUT_FORMAT_CHANGED` was ignored, so the decoder's *actual* sample rate,
 *    channel count and PCM encoding were assumed rather than read — a decoder that resamples
 *    produced a correct-looking spectrogram on the wrong frequency grid [C1 class];
 *  - failures printed a stack trace and returned null, so the caller could not tell "no audio
 *    track" from "unsupported encoding".
 *
 * Progress is reported through [onProgress] (0..1) so the UI can show real progress instead
 * of an indeterminate spinner for the many seconds a long video takes.
 */
object VideoAudioExtractor {
    private const val TAG = "VideoAudioExtractor"
    private const val DEQUEUE_TIMEOUT_US = 5_000L

    /** Initial PCM capacity (~1 M samples ≈ 24 s at 44.1 kHz); grown geometrically. */
    private const val INITIAL_PCM_CAPACITY = 1 shl 20

    /** Report progress every 2 % — often enough to look alive, rare enough to be free. */
    private const val PROGRESS_STEP = 0.02f

    private const val MILLIS_PER_SECOND = 1000L

    private class AudioTrack(
        val index: Int,
        val format: MediaFormat,
        val mime: String,
    )

    /**
     * The decode loop's mutable state, split out so the loop body reads as two steps.
     *
     * Sample rate / channel count / PCM encoding start from the *input* track format and are
     * corrected the moment the decoder announces its own output format — the two can differ,
     * and believing the input one silently puts the whole analysis on the wrong frequency
     * grid [C1 class].
     */
    private class DecodeState(
        inputFormat: MediaFormat,
    ) {
        var sampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, AudioConfig.LIVE_SAMPLE_RATE_HZ)
            private set
        var channelCount = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1)
            private set
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val maxSamples = WavDataReader.MAX_DURATION_SEC * sampleRate
        var pcm = ShortArray(minOf(maxSamples, INITIAL_PCM_CAPACITY))
            private set
        var pcmSize = 0
            private set
        var outputDone = false
            private set

        private var inputDone = false
        private var lastProgress = 0f
        private val bufferInfo = MediaCodec.BufferInfo()

        fun feedInput(
            codec: MediaCodec,
            extractor: MediaExtractor,
            containerDurationUs: Long,
            onProgress: (Float) -> Unit,
        ) {
            if (inputDone) return
            val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inIndex < 0) return

            val inputBuffer = codec.getInputBuffer(inIndex)
            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
            if (sampleSize < 0) {
                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                reportProgress(extractor.sampleTime, containerDurationUs, onProgress)
                extractor.advance()
            }
        }

        private fun reportProgress(
            sampleTimeUs: Long,
            containerDurationUs: Long,
            onProgress: (Float) -> Unit,
        ) {
            if (containerDurationUs <= 0) return
            val p = (sampleTimeUs.toFloat() / containerDurationUs).coerceIn(0f, 1f)
            if (p - lastProgress >= PROGRESS_STEP) {
                lastProgress = p
                onProgress(p)
            }
        }

        /**
         * Drains one output buffer — and keeps being called AFTER the input EOS, which is
         * exactly the tail the previous implementation threw away [C12].
         */
        fun drainOutput(codec: MediaCodec) {
            when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> adoptOutputFormat(codec.outputFormat)
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outIndex >= 0) consume(codec, outIndex)
            }
        }

        private fun adoptOutputFormat(out: MediaFormat) {
            sampleRate = out.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            channelCount = out.optInt(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
            pcmEncoding = out.optInt(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            Log.i(TAG, "decoder output: ${sampleRate}Hz x$channelCount encoding=$pcmEncoding")
        }

        private fun consume(
            codec: MediaCodec,
            outIndex: Int,
        ) {
            val outputBuffer = codec.getOutputBuffer(outIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                ensureCapacity(bufferInfo.size)
                pcmSize =
                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        appendFloat(outputBuffer, channelCount, pcm, pcmSize, maxSamples)
                    } else {
                        appendShort(outputBuffer, channelCount, pcm, pcmSize, maxSamples)
                    }
            }
            codec.releaseOutputBuffer(outIndex, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
        }

        private fun ensureCapacity(incomingBytes: Int) {
            if (pcm.size - pcmSize >= incomingBytes / 2 + channelCount) return
            pcm = pcm.copyOf(minOf(maxSamples, maxOf(pcm.size * 2, pcmSize + incomingBytes)))
        }
    }

    private fun MediaExtractor.findAudioTrack(): AudioTrack? {
        for (i in 0 until trackCount) {
            val format = getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return AudioTrack(i, format, mime)
        }
        return null
    }

    /** Typed outcome: the caller can say *why* an extraction produced nothing. */
    sealed interface Result {
        data class Success(
            val data: LoadedWavData,
        ) : Result

        data class Failure(
            val message: String,
        ) : Result
    }

    fun extractAudioFromVideoUri(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)

            val track =
                extractor.findAudioTrack()
                    ?: return Result.Failure("Cette vidéo ne contient aucune piste audio")
            val inputFormat = track.format
            extractor.selectTrack(track.index)
            // Container duration drives the progress bar only; the analyzed duration is
            // always recomputed from the PCM actually decoded [C3].
            val containerDurationUs =
                if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(track.mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val state = DecodeState(inputFormat)
            while (!state.outputDone && state.pcmSize < state.maxSamples) {
                state.feedInput(codec, extractor, containerDurationUs, onProgress)
                state.drainOutput(codec)
            }

            if (state.pcmSize == 0) {
                Result.Failure("Aucun échantillon audio décodable dans cette vidéo")
            } else {
                onProgress(1f)
                val pcmArray = state.pcm.copyOf(state.pcmSize)
                // [C3] Honest duration: what was actually extracted/analyzed, never the
                // container's claim (which exceeds the PCM for >5-min videos).
                Result.Success(
                    LoadedWavData(
                        pcmSamples = pcmArray,
                        sampleRate = state.sampleRate,
                        durationMs = (pcmArray.size.toLong() * MILLIS_PER_SECOND) / state.sampleRate,
                        telemetryList = emptyList(),
                    ),
                )
            }
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "audio extraction failed", e)
            Result.Failure("Extraction audio impossible : ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.optInt(
        key: String,
        fallback: Int,
    ): Int = if (containsKey(key)) getInteger(key) else fallback

    /**
     * Interleaved 16-bit PCM -> mono, averaging channels. Returns the new size.
     *
     * `internal` so the downmix and the 5-minute cap are unit-testable without a decoder —
     * this is where a channel-count mistake silently turns a stereo track into spectral
     * garbage [C2 class].
     */
    internal fun appendShort(
        buffer: java.nio.ByteBuffer,
        channelCount: Int,
        out: ShortArray,
        startSize: Int,
        maxSamples: Int,
    ): Int {
        val shorts = buffer.asShortBuffer()
        var size = startSize
        if (channelCount > 1) {
            while (shorts.remaining() >= channelCount && size < maxSamples) {
                var sum = 0
                repeat(channelCount) { sum += shorts.get() }
                out[size++] = (sum / channelCount).toShort()
            }
        } else {
            while (shorts.hasRemaining() && size < maxSamples) out[size++] = shorts.get()
        }
        return size
    }

    /** Interleaved float PCM (some decoders) -> mono 16-bit, clamped. Returns the new size. */
    internal fun appendFloat(
        buffer: java.nio.ByteBuffer,
        channelCount: Int,
        out: ShortArray,
        startSize: Int,
        maxSamples: Int,
    ): Int {
        val floats = buffer.asFloatBuffer()
        var size = startSize
        while (floats.remaining() >= channelCount && size < maxSamples) {
            var sum = 0f
            repeat(channelCount) { sum += floats.get() }
            val v = (sum / channelCount).coerceIn(-1f, 1f)
            out[size++] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return size
    }
}
