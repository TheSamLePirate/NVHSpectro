package com.example.nvhspectro.export

import com.example.nvhspectro.AnalysisProvenance
import com.example.nvhspectro.AudioSourceMode
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.data.OrderSearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The report's traceability block and the metric's honest name [U7, D1, D5, plan 4.5].
 *
 * The audit's finding was not a typo: a customer-facing PDF that prints an ECMA-74 metric
 * name for an in-house heuristic, with no date, build or source, is a professional-liability
 * problem. These tests hold both halves — the stamp says where the numbers came from, and the
 * metric never gets a standard's name back.
 */
class ReportStampTest {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Date =
        Calendar
            .getInstance(Locale.FRANCE)
            .apply {
                clear()
                set(year, month - 1, day, hour, minute)
            }.time

    private fun stamp(
        mode: AudioSourceMode,
        provenance: AnalysisProvenance,
        sampleRateHz: Int = 48_000,
        fftSize: Int = 2048,
    ) = ReportStamp.build(
        appVersion = "13.2.0",
        generatedAt = at(2026, 8, 27, 14, 5),
        sourceMode = mode,
        provenance = provenance,
        sampleRateHz = sampleRateHz,
        fftSize = fftSize,
    )

    @Test
    fun u7_theStamp_carriesTheBuildAndTheInstant() {
        val s = stamp(AudioSourceMode.LIVE, AnalysisProvenance(captureSourceLabel = "UNPROCESSED"))
        assertEquals("13.2.0", s.appVersion)
        assertEquals("27/08/2026 14:05", s.formattedTimestamp())
    }

    @Test
    fun c8_liveReports_nameTheMicrophoneRouteThatWasActuallyGranted() {
        val unprocessed = stamp(AudioSourceMode.LIVE, AnalysisProvenance(captureSourceLabel = "UNPROCESSED"))
        assertTrue(unprocessed.sourceLine.contains("UNPROCESSED"))

        // The fallback path is measurement-relevant (AGC/NS active) and must not be hidden.
        val fallback = stamp(AudioSourceMode.LIVE, AnalysisProvenance(captureSourceLabel = "VOICE_RECOGNITION"))
        assertTrue(fallback.sourceLine.contains("VOICE_RECOGNITION"))
    }

    @Test
    fun u7_fileReports_nameTheFile() {
        val s = stamp(AudioSourceMode.WAV_ANALYZER, AnalysisProvenance(sourceName = "Essai_A.wav"))
        assertTrue(s.sourceLine.contains("Essai_A.wav"))
        assertTrue(s.sourceLine.contains("WAV"))

        val v = stamp(AudioSourceMode.VIDEO, AnalysisProvenance(sourceName = "roulage.mp4"))
        assertTrue(v.sourceLine.contains("roulage.mp4"))
    }

    @Test
    fun u7_missingProvenance_saysSoInsteadOfImplyingAKnownSource() {
        val s = stamp(AudioSourceMode.LIVE, AnalysisProvenance())
        assertTrue(s.sourceLine.contains("non renseigné"))
    }

    @Test
    fun gps44_theSpeedLine_distinguishesCausalLiveFromDeferredSmoothing() {
        val live = stamp(AudioSourceMode.LIVE, AnalysisProvenance())
        assertTrue(live.speedLine.contains("causale"))

        val replay = stamp(AudioSourceMode.WAV_ANALYZER, AnalysisProvenance(speedStatusLabel = "lissée (RTS)"))
        assertTrue(replay.speedLine.contains("lissée (RTS)"))

        val none = stamp(AudioSourceMode.WAV_ANALYZER, AnalysisProvenance())
        assertTrue(none.speedLine.contains("aucune télémétrie"))
    }

    @Test
    fun gps42_theSpeedLine_recordsTheOrderConfidenceLevelUsed() {
        val s = stamp(AudioSourceMode.LIVE, AnalysisProvenance())
        val k = String.format(Locale.FRANCE, "%.1f", OrderSearchPolicy.CONFIDENCE_K)
        assertTrue("the report must record the k it searched with", s.speedLine.contains("k=$k"))
    }

    @Test
    fun c1_theAnalysisLine_statesTheSourcesOwnRateAndBinWidth() {
        val s = stamp(AudioSourceMode.WAV_ANALYZER, AnalysisProvenance(sourceName = "x.wav"), sampleRateHz = 48_000, fftSize = 2048)
        assertTrue(s.analysisLine.contains("48000 Hz"))
        assertTrue(s.analysisLine.contains("FFT 2048"))
        // 48000 / 2048 = 23.4375 Hz
        assertTrue("Δf must be the real bin width", s.analysisLine.contains("23,4"))
    }

    @Test
    fun d1_theEmergenceMetric_isNeverLabelledWithAStandardsName() {
        assertEquals("Indice d'émergence NVH", ReportStamp.EMERGENCE_METRIC_NAME)
        assertFalse(ReportStamp.EMERGENCE_METRIC_NAME.contains("TTNR"))
        assertFalse(ReportStamp.EMERGENCE_METRIC_NAME.contains("ECMA"))
        // The display label an operator reads carries the same honesty.
        assertFalse(DisplayMode.TTNR.label.contains("TTNR"))
        assertTrue(DisplayMode.TTNR.label.contains("mergence"))
    }

    @Test
    fun d1_theReportFootnote_statesTheMetricIsNotEcma74Conformant() {
        val note = ReportStamp.EMERGENCE_METRIC_NOTE
        assertTrue(note.contains("non conforme"))
        assertTrue(note.contains("ECMA-74"))
    }
}
