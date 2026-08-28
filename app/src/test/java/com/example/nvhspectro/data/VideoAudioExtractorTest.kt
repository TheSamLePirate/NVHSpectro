package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The decoder-output conversion, tested without a decoder [C12, plan 4.8].
 *
 * These are the parts of video extraction that silently corrupt an analysis rather than
 * failing: a wrong channel stride turns stereo into spectral garbage, an unclamped float
 * conversion wraps a loud sample to full-scale negative, and a cap that is checked in the
 * wrong place writes past the buffer.
 */
class VideoAudioExtractorTest {
    private fun shortBuffer(vararg values: Short): ByteBuffer =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putShort(it) }
            rewind()
        }

    private fun floatBuffer(vararg values: Float): ByteBuffer =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putFloat(it) }
            rewind()
        }

    @Test
    fun c12_stereoPcm_isDownmixedToMonoByAveraging() {
        val out = ShortArray(8)
        // L/R interleaved: (100,300) -> 200, (-1000,-2000) -> -1500
        val size = VideoAudioExtractor.appendShort(shortBuffer(100, 300, -1000, -2000), 2, out, 0, out.size)
        assertEquals(2, size)
        assertEquals(200, out[0].toInt())
        assertEquals(-1500, out[1].toInt())
    }

    @Test
    fun c12_monoPcm_isCopiedThroughUnchanged() {
        val out = ShortArray(8)
        val size = VideoAudioExtractor.appendShort(shortBuffer(1, -2, 3), 1, out, 0, out.size)
        assertEquals(3, size)
        assertEquals(listOf(1, -2, 3), out.take(3).map { it.toInt() })
    }

    @Test
    fun c12_appendResumesFromTheCurrentSize_soBuffersConcatenate() {
        val out = ShortArray(8)
        var size = VideoAudioExtractor.appendShort(shortBuffer(1, 2), 1, out, 0, out.size)
        size = VideoAudioExtractor.appendShort(shortBuffer(3, 4), 1, out, size, out.size)
        assertEquals(4, size)
        assertEquals(listOf(1, 2, 3, 4), out.take(4).map { it.toInt() })
    }

    @Test
    fun c12_theDurationCap_stopsWritingInsteadOfOverflowing() {
        val out = ShortArray(8)
        // maxSamples smaller than what the buffer holds: the surplus is dropped, not written.
        val size = VideoAudioExtractor.appendShort(shortBuffer(1, 2, 3, 4, 5), 1, out, 0, 3)
        assertEquals(3, size)
        assertEquals(0, out[3].toInt())
    }

    @Test
    fun c12_floatPcm_isConvertedAndClamped() {
        val out = ShortArray(8)
        val size = VideoAudioExtractor.appendFloat(floatBuffer(0f, 1f, -1f, 2.5f, -3f), 1, out, 0, out.size)
        assertEquals(5, size)
        assertEquals(0, out[0].toInt())
        assertEquals(Short.MAX_VALUE.toInt(), out[1].toInt())
        assertEquals(-Short.MAX_VALUE.toInt(), out[2].toInt())
        // Out-of-range input clamps to full scale instead of wrapping around.
        assertEquals(Short.MAX_VALUE.toInt(), out[3].toInt())
        assertEquals(-Short.MAX_VALUE.toInt(), out[4].toInt())
    }

    @Test
    fun c12_floatStereo_isDownmixedBeforeConversion() {
        val out = ShortArray(4)
        val size = VideoAudioExtractor.appendFloat(floatBuffer(1f, -1f, 0.5f, 0.5f), 2, out, 0, out.size)
        assertEquals(2, size)
        assertEquals(0, out[0].toInt())
        assertEquals((0.5f * Short.MAX_VALUE).toInt(), out[1].toInt())
    }

    @Test
    fun c12_aTrailingPartialFrame_isDroppedRatherThanMisaligned() {
        // 3 shorts on a stereo stream: the orphan sample must not become a mono frame, which
        // would shift every later sample between the L and R channel.
        val out = ShortArray(8)
        val size = VideoAudioExtractor.appendShort(shortBuffer(10, 20, 30), 2, out, 0, out.size)
        assertEquals(1, size)
        assertEquals(15, out[0].toInt())
    }
}
