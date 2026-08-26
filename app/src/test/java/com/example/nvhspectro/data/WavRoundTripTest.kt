package com.example.nvhspectro.data

import com.example.nvhspectro.testutil.SynthSignals
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * WavAudioWriter → WavDataReader round-trip — Phase 0.6 of the AAA plan.
 *
 * Scope note: the writer emits canonical 44-byte-header mono 16-bit PCM, which
 * is exactly the only layout the current reader parses correctly [audit C2].
 * These tests pin the canonical path so the Phase 1 chunk-walking rewrite
 * (plan 1.2) has a regression net; non-canonical/stereo cases get their own
 * tests in that phase.
 */
class WavRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTrip_44100_oneSecondSine() {
        val pcm = SynthSignals.sine(440.0, 44100, 44100, amplitude = 0.5)
        val f = tmp.newFile("rt44100.wav")
        WavAudioWriter.writePcmToWav(pcm, f, 44100)

        val data = WavDataReader.readWavFile(f, null)
        assertNotNull(data)
        assertEquals(44100, data!!.sampleRate)
        assertEquals(1000L, data.durationMs)
        assertEquals(pcm.size, data.pcmSamples.size)
        assertArrayEquals(pcm, data.pcmSamples)
    }

    @Test
    fun roundTrip_8000_preservesSampleRateAndSamples() {
        val pcm = SynthSignals.seededNoise(8000, seed = 7L, amplitude = 0.3)
        val f = tmp.newFile("rt8000.wav")
        WavAudioWriter.writePcmToWav(pcm, f, 8000)

        val data = WavDataReader.readWavFile(f, null)
        assertNotNull(data)
        assertEquals(8000, data!!.sampleRate)
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

        assertEquals(1, le16(20))            // PCM format
        assertEquals(1, le16(22))            // mono
        assertEquals(44100, le32(24))        // sample rate
        assertEquals(44100 * 2, le32(28))    // byte rate
        assertEquals(2, le16(32))            // block align
        assertEquals(16, le16(34))           // bits per sample
        assertEquals(2000, le32(40))         // data chunk length
        assertEquals(2000 + 36, le32(4))     // RIFF length
    }

    @Test
    fun reader_returnsNull_forNonWavFile() {
        val f = tmp.newFile("not_a_wav.wav")
        f.writeBytes(ByteArray(100) { 0x41 })
        val data = WavDataReader.readWavFile(f, null)
        assertEquals(null, data)
    }
}
