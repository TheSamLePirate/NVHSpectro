package com.example.nvhspectro.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real RIFF parser [audit C2]: walks chunks (tolerates LIST/fact/bext/JUNK…),
 * takes the audio format from the `fmt ` chunk instead of assuming a canonical
 * 44-byte layout, downmixes stereo to mono, and rejects what it cannot decode
 * honestly (non-PCM, non-16-bit) instead of producing spectral garbage.
 * Buffers are sized from the actual data chunk — no blind max-size allocation.
 */
object WavDataReader {
    const val MAX_DURATION_SEC = 5 * 60

    fun readWavFile(
        file: File,
        jsonFile: File? = null,
    ): WavReadResult {
        if (!file.exists()) return WavReadResult.Error(WavReadError.FILE_NOT_FOUND, file.name)
        return try {
            file.inputStream().buffered().use { stream ->
                parseWavStream(stream, jsonFile?.readText())
            }
        } catch (e: Exception) {
            WavReadResult.Error(WavReadError.UNREADABLE, e.message ?: e.javaClass.simpleName)
        }
    }

    fun readWavFromUri(
        context: Context,
        uri: Uri,
        jsonText: String? = null,
    ): WavReadResult {
        return try {
            val stream =
                context.contentResolver.openInputStream(uri)
                    ?: return WavReadResult.Error(WavReadError.INACCESSIBLE)
            stream.buffered().use { parseWavStream(it, jsonText) }
        } catch (e: Exception) {
            WavReadResult.Error(WavReadError.UNREADABLE, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parseWavStream(
        stream: InputStream,
        jsonText: String?,
    ): WavReadResult {
        // --- RIFF header ---
        val riff = ByteArray(12)
        if (!readFully(stream, riff, 12)) return WavReadResult.Error(WavReadError.TOO_SHORT)
        if (!riff.startsWith("RIFF") || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") {
            return WavReadResult.Error(WavReadError.NOT_RIFF)
        }

        // --- Chunk walk: find fmt, then data ---
        var fmt: FmtChunk? = null
        val chunkHeader = ByteArray(8)
        while (readFully(stream, chunkHeader, 8)) {
            val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val size =
                ByteBuffer
                    .wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFFFFFFL

            when (id) {
                "fmt " -> {
                    val toRead = size.coerceAtMost(64L).toInt()
                    val buf = ByteArray(toRead)
                    if (!readFully(stream, buf, toRead)) return WavReadResult.Error(WavReadError.FMT_TRUNCATED)
                    skipFully(stream, size - toRead + (size and 1L))
                    fmt = parseFmt(buf) ?: return WavReadResult.Error(WavReadError.FMT_UNREADABLE)
                }
                "data" -> {
                    val f = fmt ?: return WavReadResult.Error(WavReadError.FMT_MISSING)
                    val unsupported = validateFormat(f)
                    if (unsupported != null) return unsupported
                    return readData(stream, f, size, jsonText)
                }
                else -> {
                    // LIST, fact, bext, JUNK, id3 … skip, honoring the odd-size pad byte.
                    if (!skipFully(stream, size + (size and 1L))) break
                }
            }
        }
        return WavReadResult.Error(WavReadError.DATA_MISSING)
    }

    private data class FmtChunk(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
    )

    private fun parseFmt(buf: ByteArray): FmtChunk? {
        if (buf.size < 16) return null
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        var audioFormat = bb.getShort(0).toInt() and 0xFFFF
        val channels = bb.getShort(2).toInt() and 0xFFFF
        val sampleRate = bb.getInt(4)
        val bitsPerSample = bb.getShort(14).toInt() and 0xFFFF
        // WAVE_FORMAT_EXTENSIBLE: the real format is the first 2 bytes of the SubFormat GUID.
        if (audioFormat == 0xFFFE && buf.size >= 26) {
            audioFormat = bb.getShort(24).toInt() and 0xFFFF
        }
        return FmtChunk(audioFormat, channels, sampleRate, bitsPerSample)
    }

    private fun validateFormat(f: FmtChunk): WavReadResult.Unsupported? =
        when {
            f.audioFormat != 1 ->
                WavReadResult.Unsupported(WavReadError.FORMAT_UNSUPPORTED, f.audioFormat.toString())
            f.bitsPerSample != 16 ->
                WavReadResult.Unsupported(WavReadError.BITS_UNSUPPORTED, f.bitsPerSample.toString())
            f.channels !in 1..2 ->
                WavReadResult.Unsupported(WavReadError.CHANNELS_UNSUPPORTED, f.channels.toString())
            f.sampleRate !in 8000..192000 ->
                WavReadResult.Unsupported(WavReadError.SAMPLE_RATE_INVALID, f.sampleRate.toString())
            else -> null
        }

    private fun readData(
        stream: InputStream,
        f: FmtChunk,
        dataSize: Long,
        jsonText: String?,
    ): WavReadResult {
        val bytesPerFrame = 2 * f.channels
        val capBytes = MAX_DURATION_SEC.toLong() * f.sampleRate * bytesPerFrame
        val bytesToRead = dataSize.coerceAtMost(capBytes)
        val truncated = dataSize > capBytes

        val maxFrames = (bytesToRead / bytesPerFrame).toInt()
        val mono = ShortArray(maxFrames)
        var frames = 0

        val chunk = ByteArray(64 * 1024 - (64 * 1024) % bytesPerFrame)
        var remaining = bytesToRead
        var carry = 0 // leftover bytes that didn't complete a frame
        val carryBuf = ByteArray(bytesPerFrame)

        while (remaining > 0 && frames < maxFrames) {
            val want = minOf(chunk.size - carry, remaining.toInt())
            val r = stream.read(chunk, carry, want)
            if (r <= 0) break
            remaining -= r
            val available = carry + r
            val usable = available - available % bytesPerFrame
            val bb = ByteBuffer.wrap(chunk, 0, usable).order(ByteOrder.LITTLE_ENDIAN)
            if (f.channels == 1) {
                while (bb.remaining() >= 2 && frames < maxFrames) {
                    mono[frames++] = bb.short
                }
            } else {
                while (bb.remaining() >= 4 && frames < maxFrames) {
                    val l = bb.short.toInt()
                    val rr = bb.short.toInt()
                    mono[frames++] = ((l + rr) / 2).toShort()
                }
            }
            carry = available - usable
            if (carry > 0) {
                System.arraycopy(chunk, usable, carryBuf, 0, carry)
                System.arraycopy(carryBuf, 0, chunk, 0, carry)
            }
        }

        if (frames == 0) return WavReadResult.Error(WavReadError.NO_DECODABLE_DATA)
        val pcm = if (frames == mono.size) mono else mono.copyOf(frames)
        val durationMs = frames.toLong() * 1000L / f.sampleRate

        // [S2, GPS-4.3] Schema-aware decode (v3 + legacy v2/v1 sidecars).
        val decoded = TelemetryCodec.decodeDocument(jsonText)
        return WavReadResult.Success(
            LoadedWavData(
                pcmSamples = pcm,
                sampleRate = f.sampleRate,
                durationMs = durationMs,
                telemetryList = decoded.samples,
                telemetryAudioTimesNanos = decoded.audioTimesNanos,
            ),
            truncatedToCap = truncated,
        )
    }

    private fun readFully(
        stream: InputStream,
        buf: ByteArray,
        len: Int,
    ): Boolean {
        var off = 0
        while (off < len) {
            val r = stream.read(buf, off, len - off)
            if (r <= 0) return false
            off += r
        }
        return true
    }

    private fun skipFully(
        stream: InputStream,
        n: Long,
    ): Boolean {
        var remaining = n
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() < 0) return false
                remaining--
            } else {
                remaining -= skipped
            }
        }
        return true
    }

    private fun ByteArray.startsWith(ascii: String): Boolean {
        if (size < ascii.length) return false
        for (i in ascii.indices) if (this[i] != ascii[i].code.toByte()) return false
        return true
    }
}
