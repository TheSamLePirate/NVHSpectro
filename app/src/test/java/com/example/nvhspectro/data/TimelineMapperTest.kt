package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** [C17, plan 1.4] The one index/time mapping between parallel timelines. */
class TimelineMapperTest {

    @Test
    fun c17_equalSizes_isExactIdentity_theLiveCase() {
        for (i in intArrayOf(0, 1, 7, 214, 215)) {
            assertEquals(i, TimelineMapper.mapIndex(i, 216, 216))
        }
    }

    @Test
    fun c17_wavCase_framesMapProportionallyOntoTelemetry() {
        // 12,900 FFT frames vs 30 telemetry samples (a 5-min analysis of a 30 s log):
        // the old code read telemHistory[f] directly — frame 6450 must map to the
        // MIDDLE telemetry sample, not sample 6450 (out of bounds) or sample 0.
        assertEquals(0, TimelineMapper.mapIndex(0, 12900, 30))
        assertEquals(15, TimelineMapper.mapIndex(6450, 12900, 30))
        assertEquals(29, TimelineMapper.mapIndex(12899, 12900, 30))
    }

    @Test
    fun c17_upsampling_endpointsPreserved() {
        assertEquals(0, TimelineMapper.mapIndex(0, 30, 12900))
        assertEquals(12899, TimelineMapper.mapIndex(29, 30, 12900))
    }

    @Test
    fun mapIndex_outOfRangeInput_isClamped() {
        assertEquals(29, TimelineMapper.mapIndex(99999, 12900, 30))
        assertEquals(0, TimelineMapper.mapIndex(-5, 12900, 30))
    }

    @Test
    fun mapIndex_degenerateSizes_returnZero() {
        assertEquals(0, TimelineMapper.mapIndex(5, 0, 30))
        assertEquals(0, TimelineMapper.mapIndex(5, 1, 30))
        assertEquals(0, TimelineMapper.mapIndex(5, 30, 0))
    }

    @Test
    fun timeToIndex_spansTheFullList() {
        assertEquals(0, TimelineMapper.timeToIndex(0L, 300_000L, 12900))
        assertEquals(6450, TimelineMapper.timeToIndex(150_000L, 300_000L, 12900))
        assertEquals(12899, TimelineMapper.timeToIndex(300_000L, 300_000L, 12900))
    }

    @Test
    fun timeToIndex_clampsAndHandlesDegenerates() {
        assertEquals(12899, TimelineMapper.timeToIndex(999_000L, 300_000L, 12900))
        assertEquals(0, TimelineMapper.timeToIndex(-10L, 300_000L, 12900))
        assertEquals(0, TimelineMapper.timeToIndex(10L, 0L, 12900))
        assertEquals(0, TimelineMapper.timeToIndex(10L, 300_000L, 0))
    }
}
