package com.example.nvhspectro

import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.TrackedHarmonicTag
import kotlin.math.abs

/**
 * THE order-tracking engine [audit A2, D7, plan 3.2] — the single
 * implementation of order-domain folding, EMA smoothing, harmonic-tag
 * detection and emergence-report accumulation. The live pipeline and the
 * WAV sweep both consume this class; the two historical ~130-line copies
 * (which had already drifted) are gone.
 *
 * Stateful (order-domain EMA): one instance per stream — the ViewModel owns
 * one for live capture, and each WAV sweep creates a fresh local one.
 * Methods are synchronized as a belt on top of single-thread confinement
 * (reset() may be invoked from the main thread on transitions [L7]).
 */
class OrderTrackingEngine {

    private val emaOrderSpectrum = FloatArray(ORDER_BINS)

    /** One analysis frame, in the source's own bin grid ([df] = sampleRate / fftSize). */
    class Frame(
        val ttnrRow: FloatArray,
        val absRow: FloatArray,
        val df: Double,
        val speedKmh: Float,
        val rpm: Double,
        val h1FreqHz: Double
    )

    /**
     * Process one frame: fold the TTNR spectrum into order domain, blend the
     * EMA, detect emergent orders, merge them into [report] (mutated in
     * place), and return the updated active-tag list (expired tags dropped,
     * fresh detections overlaid).
     *
     * Detection only runs above [MIN_SPEED_KMH] / [MIN_RPM]; tag hold-time
     * decay runs on every call.
     */
    @Synchronized
    fun step(
        frame: Frame,
        nowMs: Long,
        holdMs: Long,
        targetOrders: List<Double>,
        activeTags: List<TrackedHarmonicTag>,
        report: MutableList<EmergenceReportEntry>
    ): List<TrackedHarmonicTag> {
        val newDetectedTags = mutableListOf<TrackedHarmonicTag>()

        if (frame.speedKmh > MIN_SPEED_KMH && frame.rpm > MIN_RPM) {
            val binCount = frame.ttnrRow.size
            val currentFrameSpectrum = FloatArray(ORDER_BINS)
            for (i in 0 until binCount) {
                val ttnrVal = frame.ttnrRow[i]
                if (ttnrVal > 0) {
                    val freqHz = i * frame.df
                    val order = freqHz / frame.h1FreqHz
                    val orderIndex = (order * ORDER_RESOLUTION).toInt()
                    if (orderIndex in 0 until ORDER_BINS) {
                        currentFrameSpectrum[orderIndex] =
                            maxOf(currentFrameSpectrum[orderIndex], ttnrVal)
                    }
                }
            }

            for (j in 0 until ORDER_BINS) {
                emaOrderSpectrum[j] = emaOrderSpectrum[j] * (1 - EMA_ALPHA) + currentFrameSpectrum[j] * EMA_ALPHA
            }

            val isWhitelistActive = targetOrders.isNotEmpty()
            for (j in 0 until ORDER_BINS) {
                if (emaOrderSpectrum[j] <= TAG_THRESHOLD_DB) continue
                if (!isLocalOrderMax(j)) continue

                val orderValue = j / ORDER_RESOLUTION
                val isAllowed = if (isWhitelistActive) {
                    targetOrders.any { abs(it - orderValue) <= WHITELIST_TOLERANCE_ORDERS }
                } else {
                    emaOrderSpectrum[j] >= OPEN_DETECTION_MIN_DB
                }
                if (!isAllowed) continue

                val orderName = "Ordre H$orderValue"
                val freqHz = (orderValue * frame.h1FreqHz).toInt()
                val binIndex = (freqHz / frame.df).toInt().coerceIn(0, binCount - 1)

                newDetectedTags.add(
                    TrackedHarmonicTag(
                        orderName = orderName,
                        orderValue = orderValue,
                        freqHz = freqHz,
                        ttnrDb = emaOrderSpectrum[j].toDouble(),
                        absDbFS = frame.absRow[binIndex].toDouble(),
                        speedKmh = frame.speedKmh,
                        rpm = frame.rpm,
                        binIndex = binIndex,
                        lastSeenTimestampMs = nowMs
                    )
                )
                mergeIntoReport(report, orderValue, orderName, freqHz, emaOrderSpectrum[j].toDouble(), frame, nowMs)
            }
        }

        val updatedTagMap = activeTags
            .filter { nowMs - it.lastSeenTimestampMs < holdMs }
            .associateBy { it.orderName }
            .toMutableMap()
        for (tag in newDetectedTags) {
            updatedTagMap[tag.orderName] = tag
        }
        return updatedTagMap.values.sortedBy { it.orderValue }
    }

    /** Strict local max over ±[LOCAL_MAX_RADIUS_BINS], left neighbor wins ties. */
    private fun isLocalOrderMax(j: Int): Boolean {
        for (k in maxOf(0, j - LOCAL_MAX_RADIUS_BINS)..minOf(ORDER_BINS - 1, j + LOCAL_MAX_RADIUS_BINS)) {
            if (emaOrderSpectrum[k] > emaOrderSpectrum[j]) return false
            if (k < j && emaOrderSpectrum[k] == emaOrderSpectrum[j]) return false
        }
        return true
    }

    private fun mergeIntoReport(
        report: MutableList<EmergenceReportEntry>,
        orderValue: Double,
        orderName: String,
        freqHz: Int,
        emergenceDb: Double,
        frame: Frame,
        nowMs: Long
    ) {
        val existing = report.find {
            abs(it.orderValue - orderValue) <= REPORT_MERGE_TOLERANCE_ORDERS &&
                frame.speedKmh <= it.maxSpeedKmh + REPORT_SPEED_MERGE_WINDOW_KMH &&
                frame.speedKmh >= it.minSpeedKmh - REPORT_SPEED_MERGE_WINDOW_KMH
        }
        if (existing != null) {
            existing.minSpeedKmh = minOf(existing.minSpeedKmh, frame.speedKmh)
            existing.maxSpeedKmh = maxOf(existing.maxSpeedKmh, frame.speedKmh)
            existing.minRpm = minOf(existing.minRpm, frame.rpm.toInt())
            existing.maxRpm = maxOf(existing.maxRpm, frame.rpm.toInt())
            existing.minFreqHz = minOf(existing.minFreqHz, freqHz)
            existing.maxFreqHz = maxOf(existing.maxFreqHz, freqHz)
            existing.maxEmergenceDb = maxOf(existing.maxEmergenceDb, emergenceDb)
            existing.countDetections++
            existing.lastTimestampMs = nowMs
        } else {
            report.add(
                EmergenceReportEntry(
                    orderName = orderName,
                    orderValue = orderValue,
                    minSpeedKmh = frame.speedKmh,
                    maxSpeedKmh = frame.speedKmh,
                    minRpm = frame.rpm.toInt(),
                    maxRpm = frame.rpm.toInt(),
                    minFreqHz = freqHz,
                    maxFreqHz = freqHz,
                    maxEmergenceDb = emergenceDb,
                    countDetections = 1,
                    lastTimestampMs = nowMs
                )
            )
        }
    }

    /** [L7] Ghost orders from a previous stream/config must never survive a transition. */
    @Synchronized
    fun reset() {
        emaOrderSpectrum.fill(0f)
    }

    /** Max level around one tracked order's target frequency. */
    class TrackedOrderLevels(val dbFS: Double, val emergenceDb: Double)

    companion object {
        /** Order axis: 0.0 … 99.9 in steps of 0.1. */
        const val ORDER_BINS = 1000
        const val ORDER_RESOLUTION = 10.0

        /** EMA blend per frame (~2.3 s to 63 % at the 43 fps live rate). */
        const val EMA_ALPHA = 0.10f

        /** An order becomes a candidate tag above this smoothed emergence. */
        const val TAG_THRESHOLD_DB = 2.0f

        /** Without a whitelist, detections additionally need this level (anti-noise). */
        const val OPEN_DETECTION_MIN_DB = 3.0f

        /** Whitelist match half-width; also used on screen when listing targets. */
        const val WHITELIST_TOLERANCE_ORDERS = 0.25

        /** Report rows within this order distance AND speed window merge [audit D7]. */
        const val REPORT_MERGE_TOLERANCE_ORDERS = 0.2
        const val REPORT_SPEED_MERGE_WINDOW_KMH = 15f

        /** Local-max window on the order axis (±0.4 order). */
        const val LOCAL_MAX_RADIUS_BINS = 4

        /** Detection gates: below these the order→frequency mapping is meaningless. */
        const val MIN_SPEED_KMH = 1.0f
        const val MIN_RPM = 100.0

        /**
         * Tracked-order (2D graph) search radius around the projected bin
         * [audit D7 — deliberately distinct]: the per-frame paths (live, WAV
         * playback cursor) use ±1 bin because speed is sampled at the frame
         * itself; the WAV telemetry sweep uses ±3 because its speeds are
         * linearly interpolated between sparse GPS samples, so the projected
         * bin carries more error.
         */
        const val TRACKED_SEARCH_RADIUS_FRAME_BINS = 1
        const val TRACKED_SEARCH_RADIUS_SWEEP_BINS = 3

        /**
         * Max |dBFS| / emergence in a ±[radiusBins] window around
         * [targetFreqHz]. Center bin is ROUNDED (the historical sweep copy
         * truncated — resolved deliberately to rounding, audit D7).
         *
         * [D9, plan 3.7] The dBFS readout is SCALLOPING-CORRECTED: a parabola
         * through the peak bin and its neighbors estimates the true tone
         * amplitude, removing the ~1.4 dB ripple the raw bin max showed as an
         * order swept across bin boundaries.
         */
        fun searchTrackedOrder(
            absRow: FloatArray,
            ttnrRow: FloatArray,
            targetFreqHz: Double,
            df: Double,
            radiusBins: Int
        ): TrackedOrderLevels {
            val totalBins = absRow.size
            if (totalBins == 0 || df <= 0.0) return TrackedOrderLevels(-120.0, 0.0)
            val centerBin = Math.round(targetFreqHz / df).toInt().coerceIn(0, totalBins - 1)
            var maxMag = -120.0
            var maxBin = -1
            var maxEm = 0.0
            val lo = (centerBin - radiusBins).coerceAtLeast(0)
            val hi = (centerBin + radiusBins).coerceAtMost(totalBins - 1)
            for (b in lo..hi) {
                if (absRow[b] > maxMag) {
                    maxMag = absRow[b].toDouble()
                    maxBin = b
                }
                if (b < ttnrRow.size && ttnrRow[b] > maxEm) maxEm = ttnrRow[b].toDouble()
            }
            return TrackedOrderLevels(scallopingCorrected(absRow, maxBin, maxMag), maxEm)
        }

        /** [D9] Parabolic peak amplitude through (b−1, b, b+1). */
        private fun scallopingCorrected(absRow: FloatArray, peakBin: Int, peakDb: Double): Double {
            if (peakBin <= 0 || peakBin >= absRow.size - 1) return peakDb
            val y1 = absRow[peakBin - 1].toDouble()
            val y2 = peakDb
            val y3 = absRow[peakBin + 1].toDouble()
            if (y2 < y1 || y2 < y3) return peakDb // not a local max: no vertex above
            val denom = y1 - 2.0 * y2 + y3
            if (denom >= 0.0) return peakDb // flat/degenerate
            val vertex = y2 - (y1 - y3) * (y1 - y3) / (8.0 * denom)
            // A correction beyond the physical Hann scalloping maximum means the
            // neighborhood is not a tone peak — reject it, never clamp it in.
            return if (vertex - y2 <= MAX_SCALLOPING_CORRECTION_DB) vertex else peakDb
        }

        /**
         * The dB-domain parabola through a Hann half-bin tone's bins
         * (−15.4, −1.43, −1.43) corrects by 1.75 dB (vertex +0.32 dB — the
         * parabola slightly overfits the kernel; residual error ≤ ~0.35 dB vs
         * the former −1.42 dB dip). Anything above this analytic worst case
         * is not tone scalloping and is rejected.
         */
        const val MAX_SCALLOPING_CORRECTION_DB = 1.8
    }
}
