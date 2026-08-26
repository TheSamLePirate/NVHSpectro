package com.example.nvhspectro

import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.TimelineMapper
import com.example.nvhspectro.data.TrackedHarmonicTag

/**
 * [plan 3.3] The pure computation core of the WAV/video analyzer — extracted
 * from the ViewModel so the full-file pipeline is JVM-testable: STFT sweep,
 * theoretical-speed interpolation and the order-tracking sweep.
 */
object WavAnalysis {

    class Spectrogram(val absList: List<FloatArray>, val ttnrList: List<FloatArray>)

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
        checkActive: () -> Unit = {}
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
        return Spectrogram(absList, ttnrList)
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
        val telemetry: TelemetryData
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
        absList: List<FloatArray>,
        ttnrList: List<FloatArray>,
        telemetrySource: List<TelemetryData>,
        config: KinematicsConfig,
        sampleRate: Int
    ): CursorState {
        val totalMs = durationMs.coerceAtLeast(1L)
        val ratio = (posMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)

        var currentAbs = FloatArray(0)
        var currentTtnr: FloatArray? = null
        var frameIdx = 0
        if (absList.isNotEmpty()) {
            frameIdx = TimelineMapper.timeToIndex(posMs, totalMs, absList.size)
            if (frameIdx in absList.indices) currentAbs = absList[frameIdx]
            if (frameIdx in ttnrList.indices) currentTtnr = ttnrList[frameIdx]
        }

        var telem = TelemetryData(gpsStatus = GpsStatus.NONE)
        if (telemetrySource.isNotEmpty()) {
            val exactIdx = ratio * (telemetrySource.size - 1)
            val idxBefore = exactIdx.toInt().coerceIn(0, telemetrySource.size - 1)
            val idxAfter = (idxBefore + 1).coerceIn(0, telemetrySource.size - 1)
            if (idxBefore != idxAfter) {
                val fraction = (exactIdx - idxBefore).toFloat()
                val before = telemetrySource[idxBefore]
                val after = telemetrySource[idxAfter]
                val interpSpeed = before.speedKmh + fraction * (after.speedKmh - before.speedKmh)
                val interpTheo = before.theoreticalSpeedKmh + fraction * (after.theoreticalSpeedKmh - before.theoreticalSpeedKmh)
                telem = before.copy(theoreticalSpeedKmh = if (interpTheo > 0.1f) interpTheo else interpSpeed)
            } else {
                val raw = telemetrySource[idxBefore]
                telem = raw.copy(theoreticalSpeedKmh = if (raw.theoreticalSpeedKmh > 0.1f) raw.theoreticalSpeedKmh else raw.speedKmh)
            }
        }

        val speedKmh = if (config.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
        val ttnr = currentTtnr
        if (config.isEnabled && speedKmh > 1.0f && currentAbs.isNotEmpty() && ttnr != null && ttnr.isNotEmpty()) {
            val h1FreqHz = config.calculateH1FreqHz(speedKmh)
            if (h1FreqHz >= 0.5) {
                val df = (sampleRate / 2.0) / currentAbs.size
                val levels = OrderTrackingEngine.searchTrackedOrder(
                    currentAbs, ttnr, config.selectedTrackedOrder * h1FreqHz, df,
                    OrderTrackingEngine.TRACKED_SEARCH_RADIUS_FRAME_BINS
                )
                telem = telem.copy(
                    ttnrDb = ttnr.maxOrNull() ?: 0f,
                    trackedOrderDbFS = levels.dbFS,
                    trackedOrderEmergenceDb = levels.emergenceDb
                )
            }
        }
        return CursorState(frameIdx, currentTtnr, telem)
    }

    class OrderSweepResult(
        /** Telemetry with per-sample tracked-order levels filled in. */
        val updatedTelemetry: List<TelemetryData>,
        /** Active tags at each FFT frame (for playback-cursor display). */
        val tagsByFrame: Map<Int, List<TrackedHarmonicTag>>,
        val report: List<EmergenceReportEntry>
    )

    /**
     * Full-file order sweep [A2, plan 3.2/3.3]: per-telemetry tracked-order
     * levels (±3-bin window — interpolated speeds carry more error), then the
     * frame-by-frame [OrderTrackingEngine] pass on a fresh engine instance.
     */
    fun orderSweep(
        absHistory: List<FloatArray>,
        ttnrHistory: List<FloatArray>,
        telemetry: List<TelemetryData>,
        config: KinematicsConfig,
        sampleRate: Int
    ): OrderSweepResult {
        val targetOrder = config.selectedTrackedOrder

        val updatedHistory = telemetry.mapIndexed { i, telem ->
            val theoSpeed = telem.theoreticalSpeedKmh
            val absIdx = TimelineMapper.mapIndex(i, telemetry.size, absHistory.size)
            val absArr = absHistory[absIdx]
            val ttnrArr = ttnrHistory[absIdx]

            var bestAbs = -120.0
            var bestTtnr = 0.0

            if (theoSpeed > 1.0f) {
                val targetFreq = config.calculateH1FreqHz(theoSpeed) * targetOrder.toFloat()
                if (targetFreq > 0f && targetFreq < sampleRate / 2) {
                    val dfSweep = (sampleRate / 2.0) / absArr.size
                    val levels = OrderTrackingEngine.searchTrackedOrder(
                        absArr, ttnrArr, targetFreq.toDouble(), dfSweep,
                        OrderTrackingEngine.TRACKED_SEARCH_RADIUS_SWEEP_BINS
                    )
                    bestAbs = levels.dbFS
                    bestTtnr = levels.emergenceDb
                }
            }
            telem.copy(trackedOrderDbFS = bestAbs, trackedOrderEmergenceDb = bestTtnr)
        }

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
            val nowMs = (frameIdx * stepDurationMs).toLong()
            val telemIdx = TimelineMapper.mapIndex(frameIdx, absHistory.size, updatedHistory.size)
            val speedKmh = updatedHistory[telemIdx].theoreticalSpeedKmh
            val currentRpm = config.calculateRpm(speedKmh)

            currentTags = sweepEngine.step(
                OrderTrackingEngine.Frame(
                    ttnrRow = ttnrHistory[frameIdx],
                    absRow = absHistory[frameIdx],
                    df = df,
                    speedKmh = speedKmh,
                    rpm = currentRpm,
                    h1FreqHz = currentRpm / 60.0
                ),
                nowMs = nowMs,
                holdMs = maxHoldMs,
                targetOrders = targetOrders,
                activeTags = currentTags,
                report = report
            )
            tagsByFrame[frameIdx] = currentTags
        }

        return OrderSweepResult(updatedHistory, tagsByFrame, report)
    }
}
