package com.example.nvhspectro.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.nvhspectro.AudioConfig
import java.nio.ByteOrder

object VideoAudioExtractor {

    fun extractAudioFromVideoUri(context: Context, uri: Uri): LoadedWavData? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                extractor.release()
                return null
            }

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else AudioConfig.LIVE_SAMPLE_RATE_HZ
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmList = ArrayList<Short>(1024 * 64)
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            val timeoutUs = 5000L
            val maxSamples = 5 * 60 * sampleRate // Cap at 5 minutes
            var totalSamplesRead = 0

            while (!isEOS && totalSamplesRead < maxSamples) {
                val inIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                        if (channelCount > 1) {
                            // Downmix stereo to mono
                            while (shortBuffer.remaining() >= channelCount) {
                                var sum = 0
                                for (ch in 0 until channelCount) {
                                    sum += shortBuffer.get()
                                }
                                pcmList.add((sum / channelCount).toShort())
                                totalSamplesRead++
                            }
                        } else {
                            while (shortBuffer.hasRemaining()) {
                                pcmList.add(shortBuffer.get())
                                totalSamplesRead++
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val pcmArray = ShortArray(pcmList.size)
            for (i in pcmList.indices) {
                pcmArray[i] = pcmList[i]
            }

            val computedDurationMs = if (durationUs > 0) durationUs / 1000L else (pcmArray.size.toLong() * 1000L) / sampleRate

            LoadedWavData(
                pcmSamples = pcmArray,
                sampleRate = sampleRate,
                durationMs = computedDurationMs,
                telemetryList = emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try { extractor.release() } catch (_: Exception) {}
            null
        }
    }
}
