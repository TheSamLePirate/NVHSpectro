package com.example.nvhspectro

import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.TrackedHarmonicTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [A2, D7, plan 3.2] The unified order-tracking engine — one implementation
 * for the live pipeline and the WAV sweep. These tests freeze the detection
 * contract the two historical copies shared (thresholds, whitelist tolerance,
 * report merging, tag hold) and the L7 reset semantics.
 */
class OrderTrackingEngineTest {

    // 44.1 kHz / 2048-point grid; H1 = 30 Hz (18 km/h at V1000 = 10 km/h).
    private val binCount = 1024
    private val df = AudioConfig.LIVE_SAMPLE_RATE_HZ / 2.0 / binCount
    private val h1FreqHz = 30.0
    private val rpm = 1800.0
    private val speedKmh = 18f

    /** Order 18 lands near bin 25 (538 Hz): its folded order index is 17.9. */
    private val order18Bin = (18.0 * h1FreqHz / df).toInt()

    private fun frame(ttnrDb: Float): OrderTrackingEngine.Frame {
        val ttnr = FloatArray(binCount)
        ttnr[order18Bin] = ttnrDb
        val abs = FloatArray(binCount) { -90f }
        // The tag's bin is re-projected from the DETECTED order value (17.9 →
        // 537 Hz → truncated bin 24), one bin below the excitation bin (25) —
        // historical behavior both copies shared. Cover the window.
        for (b in order18Bin - 1..order18Bin + 1) abs[b] = -40f
        return OrderTrackingEngine.Frame(ttnr, abs, df, speedKmh, rpm, h1FreqHz)
    }

    private fun run(
        engine: OrderTrackingEngine,
        frames: Int,
        ttnrDb: Float = 10f,
        targetOrders: List<Double> = listOf(18.0),
        report: MutableList<EmergenceReportEntry> = mutableListOf()
    ): Pair<List<TrackedHarmonicTag>, MutableList<EmergenceReportEntry>> {
        var tags = emptyList<TrackedHarmonicTag>()
        repeat(frames) { i ->
            tags = engine.step(frame(ttnrDb), nowMs = i * 23L, holdMs = 3000L, targetOrders = targetOrders, activeTags = tags, report = report)
        }
        return tags to report
    }

    @Test
    fun a2_steadyOrder_tagsAfterEmaConvergence_andAccumulatesOneReportEntry() {
        val (tags, report) = run(OrderTrackingEngine(), frames = 8)
        assertEquals("one order, one tag", 1, tags.size)
        assertEquals("folded order value", 17.9, tags[0].orderValue, 1e-9)
        assertEquals("tag reads the absolute level at the projected bin", -40.0, tags[0].absDbFS, 1e-9)
        assertEquals("steady order = ONE merged report row", 1, report.size)
        assertTrue("detections accumulate", report[0].countDetections >= 2)
        assertEquals(speedKmh, report[0].minSpeedKmh)
        assertEquals(speedKmh, report[0].maxSpeedKmh)
    }

    @Test
    fun a2_whitelist_toleranceIsAQuarterOrder() {
        // 17.9 detected vs whitelist 18.0 → |Δ| = 0.1 ≤ 0.25 → allowed.
        val (tagsNear, _) = run(OrderTrackingEngine(), frames = 8, targetOrders = listOf(18.0))
        assertEquals(1, tagsNear.size)
        // whitelist 17.0 → |Δ| = 0.9 > 0.25 → suppressed.
        val (tagsFar, _) = run(OrderTrackingEngine(), frames = 8, targetOrders = listOf(17.0))
        assertTrue("outside the ±0.25 whitelist window no tag may fire", tagsFar.isEmpty())
    }

    @Test
    fun a2_openMode_requiresThreeDbNotJustThreshold() {
        // EMA of a 2.5 dB tone converges to 2.5: above TAG_THRESHOLD_DB (2.0)
        // but below OPEN_DETECTION_MIN_DB (3.0) → never tagged without whitelist.
        val (weak, _) = run(OrderTrackingEngine(), frames = 40, ttnrDb = 2.5f, targetOrders = emptyList())
        assertTrue("open mode must gate at 3 dB", weak.isEmpty())
        val (strong, _) = run(OrderTrackingEngine(), frames = 40, ttnrDb = 10f, targetOrders = emptyList())
        assertEquals(1, strong.size)
    }

    @Test
    fun a2_tagHold_expiresAfterHoldTime() {
        val engine = OrderTrackingEngine()
        var (tags, report) = run(engine, frames = 8)
        assertEquals(1, tags.size)
        // Vehicle stops: detection gated off, decay still runs.
        val still = OrderTrackingEngine.Frame(FloatArray(binCount), FloatArray(binCount), df, 0f, 0.0, 0.0)
        tags = engine.step(still, nowMs = 1000L, holdMs = 3000L, targetOrders = listOf(18.0), activeTags = tags, report = report)
        assertEquals("inside hold time the tag survives", 1, tags.size)
        tags = engine.step(still, nowMs = 10_000L, holdMs = 3000L, targetOrders = listOf(18.0), activeTags = tags, report = report)
        assertTrue("expired tags must drop", tags.isEmpty())
    }

    @Test
    fun l7_reset_clearsOrderEmaGhosts() {
        val engine = OrderTrackingEngine()
        run(engine, frames = 20) // EMA at bin 179 ≈ 10 dB
        val silent = frame(0f)

        // WITHOUT reset a silent frame still fires the ghost tag (EMA ≈ 9 dB).
        val ghostTags = engine.step(silent, 9_999L, 3000L, listOf(18.0), emptyList(), mutableListOf())
        assertEquals("ghost EMA proves the hazard reset() exists for", 1, ghostTags.size)

        engine.reset()
        val cleanTags = engine.step(silent, 10_000L, 3000L, listOf(18.0), emptyList(), mutableListOf())
        assertTrue("after reset() no ghost order may re-fire [L7]", cleanTags.isEmpty())
    }

    @Test
    fun d7_searchTrackedOrder_roundsCenterBin() {
        val abs = FloatArray(binCount) { -100f }
        val ttnr = FloatArray(binCount)
        abs[27] = -20f
        // Target sits at 26.6 bins: rounding → 27 (the historical sweep copy
        // truncated to 26 — resolved deliberately to rounding).
        val levels = OrderTrackingEngine.searchTrackedOrder(abs, ttnr, 26.6 * df, df, radiusBins = 0)
        assertEquals(-20.0, levels.dbFS, 1e-9)
    }

    @Test
    fun d7_searchTrackedOrder_radiusBoundsTheWindow() {
        val abs = FloatArray(binCount) { -100f }
        val ttnr = FloatArray(binCount)
        abs[30] = -20f
        val center = 27.0 * df
        val narrow = OrderTrackingEngine.searchTrackedOrder(abs, ttnr, center, df, OrderTrackingEngine.TRACKED_SEARCH_RADIUS_FRAME_BINS)
        assertEquals("±1 bin must not see a peak 3 bins away", -100.0, narrow.dbFS, 1e-9)
        val wide = OrderTrackingEngine.searchTrackedOrder(abs, ttnr, center, df, OrderTrackingEngine.TRACKED_SEARCH_RADIUS_SWEEP_BINS)
        assertEquals("±3 bins reaches it", -20.0, wide.dbFS, 1e-9)
    }
}
