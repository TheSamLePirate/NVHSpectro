package com.example.nvhspectro

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan 3.3/3.4] The shared session state machine: canonical CHRONOLOGICAL
 * histories (the U9/U10 root fix), the retro-unmask patch in that order, and
 * the L7 transition contract (hooks + resettables fire on every mode change).
 */
class MeasurementSessionTest {

    private fun session() = MeasurementSession(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    private fun frame(value: Float) = FloatArray(4) { value }

    @Test
    fun u9_liveHistories_areChronological_newestLast() {
        val s = session()
        for (v in 1..3) {
            s.appendLiveFrame(frame(v.toFloat()), frame(v * 10f), emptyList(), emptyList(), maxHistory = 10)
            s.appendLiveTelemetry(TelemetryData(speedKmh = v.toFloat()), maxHistory = 10)
        }
        assertEquals("oldest frame first", 1f, s.fftHistoryAbsolute.value.first()[0], 1e-6f)
        assertEquals("newest frame LAST", 3f, s.fftHistoryAbsolute.value.last()[0], 1e-6f)
        assertEquals(30f, s.fftHistoryTTNR.value.last()[0], 1e-6f)
        assertEquals(1f, s.telemetryHistory.value.first().speedKmh)
        assertEquals(3f, s.telemetryHistory.value.last().speedKmh)
    }

    @Test
    fun u9_liveHistory_trimsTheOldestFrame() {
        val s = session()
        for (v in 1..5) {
            s.appendLiveFrame(frame(v.toFloat()), frame(v.toFloat()), emptyList(), emptyList(), maxHistory = 3)
        }
        assertEquals(3, s.fftHistoryAbsolute.value.size)
        assertEquals("trim drops the OLDEST", 3f, s.fftHistoryAbsolute.value.first()[0], 1e-6f)
        assertEquals(5f, s.fftHistoryAbsolute.value.last()[0], 1e-6f)
    }

    @Test
    fun retroUnmask_patchesTheMostRecentRows() {
        val s = session()
        // 6 plain frames, then one carrying a retro unmask of bin 2 for the
        // 5 previous rows (raw values 91..95, k=1 the most recent).
        repeat(6) { s.appendLiveFrame(frame(0f), frame(-100f), emptyList(), emptyList(), 20) }
        val retroRows = (1..5).map { k -> FloatArray(4) { 90f + k } }
        s.appendLiveFrame(frame(0f), frame(5f), listOf(2), retroRows, 20)

        val ttnr = s.fftHistoryTTNR.value
        assertEquals("row k frames ago gets raw row k", 91f, ttnr[ttnr.lastIndex - 1][2], 1e-6f)
        assertEquals(95f, ttnr[ttnr.lastIndex - 5][2], 1e-6f)
        assertEquals("untouched bins stay masked", -100f, ttnr[ttnr.lastIndex - 1][0], 1e-6f)
        assertEquals("older rows untouched", -100f, ttnr[ttnr.lastIndex - 6][2], 1e-6f)
    }

    @Test
    fun l7_modeTransition_firesHooksThenResetsAndClears() {
        val s = session()
        val order = mutableListOf<String>()
        s.registerModeTransitionHook { order.add("hook:$it") }
        s.registerAnalysisResettable { order.add("reset") }

        s.appendLiveFrame(frame(1f), frame(1f), emptyList(), emptyList(), 10)
        s.setLatestTtnrSpectrum(frame(9f))
        s.setTrackedHarmonicTags(emptyList())

        s.setAudioSourceMode(AudioSourceMode.WAV_ANALYZER)
        assertEquals(listOf("hook:WAV_ANALYZER", "reset"), order)
        assertTrue("streams cleared", s.fftHistoryAbsolute.value.isEmpty())
        assertEquals("latest TTNR wiped", 0, s.latestTTNRSpectrum.value.size)

        // Same-mode call is a no-op.
        s.setAudioSourceMode(AudioSourceMode.WAV_ANALYZER)
        assertEquals(2, order.size)
    }

    @Test
    fun l7_unregister_stopsFutureCallbacks() {
        val s = session()
        var calls = 0
        val unregister = s.registerAnalysisResettable { calls++ }
        s.resetAnalysisState()
        unregister()
        s.resetAnalysisState()
        assertEquals(1, calls)
    }

    @Test
    fun c14_displayRange_clampsMinFiveDbBelowMax() {
        val s = session()
        s.updateDisplaySettings(newMinDb = -2.0, newMaxDb = -4.0, newMinFreq = 0, newMaxFreq = 8000, newTimeWindowSec = 5.0)
        assertEquals(-4.0, s.maxDb.value, 1e-12)
        assertEquals(-9.0, s.minDb.value, 1e-12)
    }
}
