package com.example.nvhspectro.data

import com.example.nvhspectro.testutil.SynthSignals
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * WavAudioWriter → WavDataReader round-trip plus the C2 chunk-walking codec
 * tests [plan 1.2]: non-canonical chunk layouts, stereo downmix, and typed
 * rejection of formats the app cannot decode honestly.
 */
class WavRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun readAsSuccess(f: File): LoadedWavData {
        val result = WavDataReader.readWavFile(f, null)
        assertTrue("expected Success, got $result", result is WavReadResult.Success)
        val success = result as WavReadResult.Success
        assertFalse("short files must not report truncation", success.truncatedToCap)
        return success.data
    }

    // ------------------------------------------------------------------
    // Round trip (canonical writer output)
    // ------------------------------------------------------------------

    @Test
    fun roundTrip_44100_oneSecondSine() {
        val pcm = SynthSignals.sine(440.0, 44100, 44100, amplitude = 0.5)
        val f = tmp.newFile("rt44100.wav")
        WavAudioWriter.writePcmToWav(pcm, f, 44100)

        val data = readAsSuccess(f)
        assertEquals(44100, data.sampleRate)
        assertEquals(1000L, data.durationMs)
        assertArrayEquals(pcm, data.pcmSamples)
    }

    @Test
    fun roundTrip_8000_preservesSampleRateAndSamples() {
        val pcm = SynthSignals.seededNoise(8000, seed = 7L, amplitude = 0.3)
        val f = tmp.newFile("rt8000.wav")
        WavAudioWriter.writePcmToWav(pcm, f, 8000)

        val data = readAsSuccess(f)
        assertEquals(8000, data.sampleRate)
        assertEquals(1000L, data.durationMs)
        assertArrayEquals(pcm, data.pcmSamples)
    }

    /** Independent byte-level check of the written header (not via the reader). */
    @Test
    fun writtenHeader_fieldsAreCanonicalPcm() {
        val pcm = ShortArray(1000) { (it % 100).toShort() }
        val f = tmp.newFile("hdr.wav")
        WavAudioWriter.writePcmToWav(pcm, f, 44100)

        val bytes = f.readBytes()
        assertEquals(44 + 2000, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))

        fun le32(off: Int) = ByteBuffer.wrap(bytes, off, 4).order(ByteOrder.LITTLE_ENDIAN).int
        fun le16(off: Int) = ByteBuffer.wrap(bytes, off, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

        assertEquals(1, le16(20))
        assertEquals(1, le16(22))
        assertEquals(44100, le32(24))
        assertEquals(44100 * 2, le32(28))
        assertEquals(2, le16(32))
        assertEquals(16, le16(34))
        assertEquals(2000, le32(40))
        assertEquals(2000 + 36, le32(4))
    }

    // ------------------------------------------------------------------
    // C2 — chunk walking, stereo, typed rejection
    // ------------------------------------------------------------------

    @Test
    fun c2_listChunkBeforeData_isSkippedCorrectly() {
        val pcm = ShortArray(500) { (it * 3).toShort() }
        val f = tmp.newFile("list.wav")
        f.writeBytes(
            buildWav(48000, channels = 1, bits = 16, pcmBytes = pcmToBytes(pcm), extraChunkBeforeData = true)
        )
        val data = readAsSuccess(f)
        assertEquals(48000, data.sampleRate)
        assertArrayEquals(pcm, data.pcmSamples)
    }

    @Test
    fun c2_stereo_downmixesToMono() {
        // L = 1000, R = 3000 → mono (1000+3000)/2 = 2000
        val frames = 300
        val bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            bytes.putShort(1000)
            bytes.putShort(3000)
        }
        val f = tmp.newFile("stereo.wav")
        f.writeBytes(buildWav(44100, channels = 2, bits = 16, pcmBytes = bytes.array()))

        val data = readAsSuccess(f)
        assertEquals(frames, data.pcmSamples.size)
        assertTrue(data.pcmSamples.all { it == 2000.toShort() })
        // Duration counts FRAMES, not interleaved samples (the old parser doubled it).
        assertEquals(frames * 1000L / 44100L, data.durationMs)
    }

    @Test
    fun c2_24bit_rejectedAsUnsupported() {
        val f = tmp.newFile("24bit.wav")
        f.writeBytes(buildWav(44100, channels = 1, bits = 24, pcmBytes = ByteArray(300)))
        val result = WavDataReader.readWavFile(f, null)
        assertTrue("expected Unsupported, got $result", result is WavReadResult.Unsupported)
        // [§12, plan 4.4] The reason is typed, and the offending value travels with it —
        // asserting on a French sentence would break the moment the app is localised.
        val unsupported = result as WavReadResult.Unsupported
        assertEquals(WavReadError.BITS_UNSUPPORTED, unsupported.reason)
        assertEquals("24", unsupported.detail)
    }

    @Test
    fun c2_floatFormat_rejectedAsUnsupported() {
        val f = tmp.newFile("float.wav")
        f.writeBytes(
            buildWav(44100, channels = 1, bits = 32, pcmBytes = ByteArray(400), audioFormat = 3),
        )
        val result = WavDataReader.readWavFile(f, null)
        assertTrue("expected Unsupported, got $result", result is WavReadResult.Unsupported)
        assertEquals(WavReadError.FORMAT_UNSUPPORTED, (result as WavReadResult.Unsupported).reason)
    }

    @Test
    fun c2_trailingChunkAfterData_isNotDecodedAsAudio() {
        val pcm = ShortArray(200) { 42 }
        val f = tmp.newFile("trailing.wav")
        val out = ByteArrayOutputStream()
        out.write(buildWav(44100, channels = 1, bits = 16, pcmBytes = pcmToBytes(pcm)))
        // Append a bogus trailing chunk — the old parser would have read it as samples.
        out.write("junk".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(8, 0, 0, 0))
        out.write(ByteArray(8) { 0x7F })
        f.writeBytes(out.toByteArray())

        val data = readAsSuccess(f)
        assertEquals(200, data.pcmSamples.size)
    }

    @Test
    fun c2_garbageFile_returnsError() {
        val f = tmp.newFile("not_a_wav.wav")
        f.writeBytes(ByteArray(100) { 0x41 })
        val result = WavDataReader.readWavFile(f, null)
        assertTrue(result is WavReadResult.Error)
        assertEquals(WavReadError.NOT_RIFF, (result as WavReadResult.Error).reason)
    }

    @Test
    fun c2_missingFile_returnsError() {
        val result = WavDataReader.readWavFile(File(tmp.root, "absent.wav"), null)
        assertTrue(result is WavReadResult.Error)
        assertEquals(WavReadError.FILE_NOT_FOUND, (result as WavReadResult.Error).reason)
    }

    // ------------------------------------------------------------------

    private fun pcmToBytes(pcm: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bb.putShort(it) }
        return bb.array()
    }

    /** Builds a WAV byte stream with configurable format and optional pre-data LIST chunk. */
    private fun buildWav(
        sampleRate: Int,
        channels: Int,
        bits: Int,
        pcmBytes: ByteArray,
        extraChunkBeforeData: Boolean = false,
        audioFormat: Int = 1,
    ): ByteArray {
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        fmt.putShort(audioFormat.toShort())
        fmt.putShort(channels.toShort())
        fmt.putInt(sampleRate)
        fmt.putInt(sampleRate * channels * bits / 8)
        fmt.putShort((channels * bits / 8).toShort())
        fmt.putShort(bits.toShort())

        val body = ByteArrayOutputStream()
        writeChunk(body, "fmt ", fmt.array())
        if (extraChunkBeforeData) {
            writeChunk(body, "LIST", "INFOIART".toByteArray(Charsets.US_ASCII) + ByteArray(9))
        }
        writeChunk(body, "data", pcmBytes)

        val riff = ByteArrayOutputStream()
        riff.write("RIFF".toByteArray(Charsets.US_ASCII))
        riff.write(le32(4 + body.size()))
        riff.write("WAVE".toByteArray(Charsets.US_ASCII))
        riff.write(body.toByteArray())
        return riff.toByteArray()
    }

    private fun writeChunk(
        out: ByteArrayOutputStream,
        id: String,
        payload: ByteArray,
    ) {
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(le32(payload.size))
        out.write(payload)
        if (payload.size % 2 == 1) out.write(0) // RIFF pad byte
    }

    private fun le32(v: Int): ByteArray =
        ByteBuffer
            .allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(v)
            .array()
}
