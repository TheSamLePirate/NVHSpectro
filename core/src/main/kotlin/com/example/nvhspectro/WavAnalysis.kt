package com.example.nvhspectro

import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.OrderSearchPolicy
import com.example.nvhspectro.data.TimelineMapper
import com.example.nvhspectro.data.TrackedHarmonicTag

/**
 * [plan 3.3] The pure computation core of the WAV/video analyzer — extracted
 * from the ViewModel so the full-file pipeline is JVM-testable: STFT sweep,
 * theoretical-speed interpolation and the order-tracking sweep.
 */
object WavAnalysis {
    private const val NO_SIGNAL_DBFS = -120.0

    class Spectrogram(
        val absList: List<FloatArray>,
        val ttnrList: List<FloatArray>,
        /** The rate the STFT ran at — every bin↔Hz mapping derives from it [C1]. */
        val sampleRateHz: Int,
    ) {
        fun dfHzAt(frameIdx: Int): Double = (sampleRateHz / 2.0) / absList[frameIdx].size
    }

    /**
     * Full-file STFT at 50 % overlap on a fresh [FFTProcessor] (stateful —
     * never share with the live stream [audit D3]). Returns null when the
     * PCM is shorter than one FFT frame. [checkActive] is invoked per frame
     * so a cancelled coroutine stops the sweep.
     */
    fun computeSpectrogram(
        pcm: ShortArray,
        sampleRate: Int,
        fftSize: Int,
        checkActive: () -> Unit = {},
    ): Spectrogram? {
        val stepSize = fftSize / 2
        val totalSamples = pcm.size
        if (totalSamples < fftSize) return null

        val processor = FFTProcessor(fftSize, sampleRate)
        val frameCount = ((totalSamples - fftSize) / stepSize).coerceAtLeast(1)
        val absList = ArrayList<FloatArray>(frameCount)
        val ttnrList = ArrayList<FloatArray>(frameCount)

        val frameBuffer = ShortArray(fftSize)
        for (i in 0 until frameCount) {
            checkActive()
            val startSample = i * stepSize
            val copyLen = (totalSamples - startSample).coerceAtMost(fftSize)
            if (copyLen > 0) {
                System.arraycopy(pcm, startSample, frameBuffer, 0, copyLen)
            } else {
                java.util.Arrays.fill(frameBuffer, 0.toShort())
            }
            val magnitudes = processor.processFFT(frameBuffer)
            ttnrList.add(processor.computeTTNR(magnitudes).toFloatSpectrum())
            absList.add(magnitudes.toFloatSpectrum())
        }
        return Spectrogram(absList, ttnrList, sampleRate)
    }

    /**
     * When an imported telemetry track carries no theoretical speed, derive
     * one by linear interpolation between the GPS speed's corner points
     * (historical behavior of the load path).
     */
    fun interpolateTheoreticalSpeed(telemetry: List<TelemetryData>): List<TelemetryData> {
        val hasTheo = telemetry.any { it.theoreticalSpeedKmh > 0.1f }
        if (hasTheo || telemetry.size <= 1) return telemetry

        val smoothList = telemetry.toMutableList()
        val corners = mutableListOf(0)
        for (i in 1 until smoothList.size) {
            if (smoothList[i].speedKmh != smoothList[i - 1].speedKmh) {
                corners.add(i)
            }
        }
        if (corners.last() != smoothList.size - 1) {
            corners.add(smoothList.size - 1)
        }

        for (c in 0 until corners.size - 1) {
            val startIdx = corners[c]
            val endIdx = corners[c + 1]
            val startSpeed = smoothList[startIdx].speedKmh
            val endSpeed = smoothList[endIdx].speedKmh
            val range = (endIdx - startIdx).toFloat().coerceAtLeast(1f)
            for (i in startIdx..endIdx) {
                val fraction = (i - startIdx).toFloat() / range
                smoothList[i] = smoothList[i].copy(theoreticalSpeedKmh = startSpeed + fraction * (endSpeed - startSpeed))
            }
        }
        return smoothList
    }

    class CursorState(
        val frameIndex: Int,
        /** TTNR row under the cursor, or null when no history exists. */
        val ttnrSpectrum: FloatArray?,
        val telemetry: TelemetryData,
    )

    /**
     * State displayed at playback position [posMs]: the FFT frame under the
     * cursor and the (interpolated) telemetry with tracked-order levels
     * (±1 bin — the cursor uses per-frame speeds) [C17: all cross-timeline
     * lookups via TimelineMapper].
     */
    fun cursorStateAt(
        posMs: Long,
        durationMs: Long,
        spectrogram: Spectrogram,
        telemetrySource: List<TelemetryData>,
        config: KinematicsConfig,
    ): CursorState {
        val totalMs = durationMs.coerceAtLeast(1L)
        val ratio = (posMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)
        val absList = spectrogram.absList
        val ttnrList = spectrogram.ttnrList

        var currentTtnr: FloatArray? = null
        var frameIdx = 0
        if (absList.isNotEmpty()) {
            frameIdx = TimelineMapper.timeToIndex(posMs, totalMs, absList.size)
            if (frameIdx in ttnrList.indices) currentTtnr = ttnrList[frameIdx]
        }

        var telem = cursorTelemetryAt(ratio, telemetrySource)
        val speedKmh = if (config.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
        if (config.isEnabled && speedKmh > 1.0f && frameIdx in absList.indices) {
            val row = ttnrList.getOrNull(frameIdx)
            if (row != null && row.isNotEmpty()) {
                telem =
                    withTrackedOrderReadout(
                        telem.copy(ttnrDb = row.maxOrNull() ?: 0f),
                        config,
                        spectrogram,
                        frameIdx,
                        legacyRadiusBins = OrderTrackingEngine.TRACKED_SEARCH_RADIUS_FRAME_BINS,
                    )
            }
        }
        return CursorState(frameIdx, currentTtnr, telem)
    }

    /** Telemetry sample interpolated at [ratio] of the recording (historical semantics). */
    private fun cursorTelemetryAt(
        ratio: Double,
        telemetrySource: List<TelemetryData>,
    ): TelemetryData {
        if (telemetrySource.isEmpty()) return TelemetryData(gpsStatus = GpsStatus.NONE)
        val exactIdx = ratio * (telemetrySource.size - 1)
        val idxBefore = exactIdx.toInt().coerceIn(0, telemetrySource.size - 1)
        val idxAfter = (idxBefore + 1).coerceIn(0, telemetrySource.size - 1)
        return if (idxBefore != idxAfter) {
            val fraction = (exactIdx - idxBefore).toFloat()
            val before = telemetrySource[idxBefore]
            val after = telemetrySource[idxAfter]
            val interpSpeed = before.speedKmh + fraction * (after.speedKmh - before.speedKmh)
            val interpTheo = before.theoreticalSpeedKmh + fraction * (after.theoreticalSpeedKmh - before.theoreticalSpeedKmh)
            before.copy(theoreticalSpeedKmh = if (interpTheo > 0.1f) interpTheo else interpSpeed)
        } else {
            val raw = telemetrySource[idxBefore]
            raw.copy(
                theoreticalSpeedKmh = if (raw.theoreticalSpeedKmh > 0.1f) raw.theoreticalSpeedKmh else raw.speedKmh,
            )
        }
    }

    /**
     * [GPS-10, GPS-4.2] The tracked-order readout behind the σ-driven window;
     * legacy sidecars (σ null) keep the historical fixed radius. A window
     * beyond the identifiability bound SUSPENDS the readout instead of
     * reporting an ambiguous level.
     */
    private fun withTrackedOrderReadout(
        telem: TelemetryData,
        config: KinematicsConfig,
        spectrogram: Spectrogram,
        frameIdx: Int,
        legacyRadiusBins: Int,
    ): TelemetryData {
        val absRow = spectrogram.absList[frameIdx]
        val ttnrRow = spectrogram.ttnrList[frameIdx]
        val dfHz = spectrogram.dfHzAt(frameIdx)
        val h1FreqHz = config.calculateH1FreqHz(telem.theoreticalSpeedKmh)
        val targetFreq = config.selectedTrackedOrder * h1FreqHz
        val sigmaF =
            telem.theoreticalSpeedSigmaKmh?.let {
                OrderSearchPolicy.sigmaOrderFreqHz(
                    config.selectedTrackedOrder,
                    it.toDouble(),
                    config.getEffectiveV1000(),
                )
            }
        val window = OrderSearchPolicy.windowFor(sigmaF, h1FreqHz, dfHz, legacyRadiusBins)
        return when {
            h1FreqHz < 0.5 || targetFreq <= 0.0 || targetFreq >= dfHz * absRow.size -> telem
            !window.identifiable ->
                telem.copy(
                    trackedOrderDbFS = NO_SIGNAL_DBFS,
                    trackedOrderEmergenceDb = 0.0,
                    trackedOrderIdentifiable = false,
                )
            else -> {
                val levels =
                    OrderTrackingEngine.searchTrackedOrder(absRow, ttnrRow, targetFreq, dfHz, window.radiusBins)
                telem.copy(
                    trackedOrderDbFS = levels.dbFS,
                    trackedOrderEmergenceDb = levels.emergenceDb,
                    trackedOrderIdentifiable = true,
                )
            }
        }
    }

    class OrderSweepResult(
        /** Telemetry with per-sample tracked-order levels filled in. */
        val updatedTelemetry: List<TelemetryData>,
        /** Active tags at each FFT frame (for playback-cursor display). */
        val tagsByFrame: Map<Int, List<TrackedHarmonicTag>>,
        val report: List<EmergenceReportEntry>,
    )

    /**
     * First pass of [orderSweep]: per-telemetry-sample tracked-order levels,
     * read straight off the spectrogram at each sample's mapped frame.
     */
    private fun trackedOrderLevels(
        spectrogram: Spectrogram,
        telemetry: List<TelemetryData>,
        config: KinematicsConfig,
        checkActive: () -> Unit,
    ): List<TelemetryData> =
        telemetry.mapIndexed { i, telem ->
            checkActive()
            val absIdx = TimelineMapper.mapIndex(i, telemetry.size, spectrogram.absList.size)
            val reset =
                telem.copy(
                    trackedOrderDbFS = NO_SIGNAL_DBFS,
                    trackedOrderEmergenceDb = 0.0,
                    trackedOrderIdentifiable = true,
                )
            if (telem.theoreticalSpeedKmh > 1.0f) {
                // [GPS-10] ±3-bin legacy fallback here — interpolated
                // sweep speeds carry more error [audit D7].
                withTrackedOrderReadout(
                    reset,
                    config,
                    spectrogram,
                    absIdx,
                    legacyRadiusBins = OrderTrackingEngine.TRACKED_SEARCH_RADIUS_SWEEP_BINS,
                )
            } else {
                reset
            }
        }

    /**
     * Full-file order sweep [A2, plan 3.2/3.3]: per-telemetry tracked-order
     * levels (±3-bin window — interpolated speeds carry more error), then the
     * frame-by-frame [OrderTrackingEngine] pass on a fresh engine instance.
     *
     * Cost is O(frames × ORDER_BINS): ~12,900 frames for a 5-minute file, each
     * folding a full spectrum into the 1000-order grid. **Never call this on
     * the main thread** [V13.2 C-1]. [checkActive] is invoked once per
     * telemetry sample and once per frame so a cancelled coroutine stops the
     * sweep instead of finishing work nobody will read.
     */
    fun orderSweep(
        spectrogram: Spectrogram,
        telemetry: List<TelemetryData>,
        config: KinematicsConfig,
        checkActive: () -> Unit = {},
    ): OrderSweepResult {
        val absHistory = spectrogram.absList
        val ttnrHistory = spectrogram.ttnrList
        val sampleRate = spectrogram.sampleRateHz
        val updatedHistory = trackedOrderLevels(spectrogram, telemetry, config, checkActive)

        val targetOrders = config.parsedTargetOrders()
        val maxHoldMs = (config.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)
        val stepDurationMs = (AudioConfig.WAV_FFT_SIZE / 2).toDouble() / sampleRate * 1000.0

        var currentTags = listOf<TrackedHarmonicTag>()
        val report = mutableListOf<EmergenceReportEntry>()
        val tagsByFrame = mutableMapOf<Int, List<TrackedHarmonicTag>>()

        val sweepEngine = OrderTrackingEngine()
        val binCount = if (absHistory.isNotEmpty()) absHistory[0].size else 1
        val df = (sampleRate / 2.0) / binCount

        for (frameIdx in absHistory.indices) {
            checkActive()
            val nowMs = (frameIdx * stepDurationMs).toLong()
            val telemIdx = TimelineMapper.mapIndex(frameIdx, absHistory.size, updatedHistory.size)
            val speedKmh = updatedHistory[telemIdx].theoreticalSpeedKmh
            val currentRpm = config.calculateRpm(speedKmh)

            currentTags =
                sweepEngine.step(
                    OrderTrackingEngine.Frame(
                        ttnrRow = ttnrHistory[frameIdx],
                        absRow = absHistory[frameIdx],
                        df = df,
                        speedKmh = speedKmh,
                        rpm = currentRpm,
                        h1FreqHz = currentRpm / 60.0,
                    ),
                    nowMs = nowMs,
                    holdMs = maxHoldMs,
                    targetOrders = targetOrders,
                    activeTags = currentTags,
                    report = report,
                )
            tagsByFrame[frameIdx] = currentTags
        }

        return OrderSweepResult(updatedHistory, tagsByFrame, report)
    }
}
