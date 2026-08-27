package com.example.nvhspectro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [plan-gps GPS-1.2, GPS-03] The frame-index → BOOTTIME mapping behind CapturedAudioFrame. */
class AudioFrameClockTest {
    private val rate = AudioConfig.LIVE_SAMPLE_RATE_HZ

    @Test
    fun gps03_frameTime_isLinearFromTheAnchorAtTheNominalRate() {
        val clock = AudioFrameClock(rate)
        clock.setAnchor(framePosition = rate.toLong(), nanoTime = 1_000_000_000L)
        // Half a second of frames after the anchor = exactly +0.5 s.
        assertEquals(1_500_000_000L, clock.frameTimeNanos(rate + rate / 2L))
    }

    @Test
    fun gps03_frameBeforeTheAnchor_mapsBackwardOnFirstQuery() {
        val clock = AudioFrameClock(rate)
        clock.setAnchor(framePosition = 2L * rate, nanoTime = 3_000_000_000L)
        // One second of frames before the anchor = exactly −1 s.
        assertEquals(2_000_000_000L, clock.frameTimeNanos(rate.toLong()))
    }

    @Test
    fun gps03_audioFrames_haveMonotonicCaptureTimestamps() {
        val clock = AudioFrameClock(rate)
        val step = 1024L
        clock.setAnchor(0L, 0L)
        var last = Long.MIN_VALUE
        var frame = 0L
        for (i in 0 until 200) {
            // Hardware anchors jitter, including slightly BACKWARD.
            if (i % 32 == 0) {
                val jitterNanos = if (i % 64 == 0) -3_000_000L else 2_000_000L
                clock.setAnchor(frame, frame * 1_000_000_000L / rate + jitterNanos)
            }
            val first = clock.frameTimeNanos(frame)
            val center = clock.frameTimeNanos(frame + step)
            assertTrue("first sample time regressed at frame $i", first >= last)
            assertTrue("center before first at frame $i", center >= first)
            last = center
            frame += step
        }
    }

    @Test
    fun gps03_backwardAnchorStep_isClampedNotPropagated() {
        val clock = AudioFrameClock(rate)
        clock.setAnchor(0L, 1_000_000_000L)
        val t1 = clock.frameTimeNanos(rate.toLong()) // = 2 s
        // A later anchor claims the same frame happened 50 ms EARLIER.
        clock.setAnchor(rate.toLong(), 1_950_000_000L)
        val t2 = clock.frameTimeNanos(rate.toLong())
        assertTrue("time must never go backward", t2 >= t1)
    }

    @Test
    fun gps03_queryBeforeAnyAnchor_failsFastInsteadOfInventingTime() {
        assertThrows(IllegalStateException::class.java) {
            AudioFrameClock(rate).frameTimeNanos(0L)
        }
    }
}
