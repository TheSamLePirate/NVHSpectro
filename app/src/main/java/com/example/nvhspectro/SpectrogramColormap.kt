package com.example.nvhspectro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.ManualOrderAnchor
import com.example.nvhspectro.data.SmartTrackedOrder
import com.example.nvhspectro.data.TrackedHarmonicTag
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.FilterType
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.ui.rememberPlotDimens
import com.example.nvhspectro.ui.spectroGeometry
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhStatusBad
import kotlin.math.max
import kotlin.math.min

/**
 * Data class représentant un pic d'émergence tonale détecté sur la trame courante
 */
data class EmergencePeak(
    val binIndex: Int,
    val freqHz: Int,
    val ttnrDb: Double,
    val absDbFS: Double,
)

/**
 * Retourne un Int ARGB basé sur la colormap "Jet"
 */
fun getJetColorInt(v: Float): Int {
    var v = max(0f, min(1f, v))
    var r = 1f
    var g = 1f
    var b = 1f
    when {
        v < 0.125f -> {
            r = 0f
            g = 0f
            b = 0.5f + 4f * v
        }
        v < 0.375f -> {
            r = 0f
            g = 4f * (v - 0.125f)
            b = 1f
        }
        v < 0.625f -> {
            r = 4f * (v - 0.375f)
            g = 1f
            b = 1f - 4f * (v - 0.375f)
        }
        v < 0.875f -> {
            r = 1f
            g = 1f - 4f * (v - 0.625f)
            b = 0f
        }
        else -> {
            r = 1f - 4f * (v - 0.875f)
            g = 0f
            b = 0f
        }
    }
    return AndroidColor.argb(
        255,
        (r * 255).toInt(),
        (g * 255).toInt(),
        (b * 255).toInt(),
    )
}

@Composable
fun SpectrogramCanvas(
    history: List<FloatArray>,
    absHistory: List<FloatArray> = emptyList(),
    ttnrHistory: List<FloatArray> = emptyList(),
    modifier: Modifier = Modifier,
    minDb: Double = -120.0,
    maxDb: Double = 0.0,
    minFreq: Int = 0,
    maxFreq: Int = 10000,
    fftSize: Int = AudioConfig.DEFAULT_FFT_SIZE,
    sampleRate: Int,
    historySize: Int = 150,
    displayMode: DisplayMode = DisplayMode.ABSOLUTE,
    isDetectorEnabled: Boolean = true,
    emergenceThresholdDb: Double = 2.5,
    magnitudeGateDbFS: Double = -90.0,
    trackedHarmonicTags: List<TrackedHarmonicTag> = emptyList(),
    kinematicsConfig: KinematicsConfig = KinematicsConfig(),
    isWavAnalyzerMode: Boolean = false,
    wavPlaybackProgress: Float = 0f,
    showH1Overlay: Boolean = false,
    projectedOrder: Double = 1.0,
    telemetryHistory: List<TelemetryData> = emptyList(),
    activeFilters: List<AudioFilter> = emptyList(),
    isReportModeActive: Boolean = false,
    isDrawingMode: Boolean = false,
    currentUserPoints: List<ManualOrderAnchor> = emptyList(),
    currentSmartPath: List<ManualOrderAnchor> = emptyList(),
    manualTrackedOrders: List<SmartTrackedOrder> = emptyList(),
    selectedManualOrder: SmartTrackedOrder? = null,
    isBrillanceModeEnabled: Boolean = false,
    onAddManualPoint: (Int, Int) -> Unit = { _, _ -> },
) {
    // [U3, plan 4.2] Margins, canvas text and strokes come from ONE dp/sp source, converted
    // for this device's density and font scale — never from raw pixel literals, and never
    // duplicated between the touch handlers and the draw pass.
    val dimens = rememberPlotDimens()
    val context = LocalContext.current

    if (history.isEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {}
        return
    }

    val totalBinCount = history.first().size
    val nyquistFreq = sampleRate / 2
    val minBin = ((minFreq * totalBinCount) / nyquistFreq).coerceIn(0, totalBinCount - 1)
    val maxBin = ((maxFreq * totalBinCount) / nyquistFreq).coerceIn(minBin + 1, totalBinCount)
    val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
    val actualMinFreq = (minBin * nyquistFreq) / totalBinCount
    val actualMaxFreq = (maxBin * nyquistFreq) / totalBinCount

    // [P1, plan 3.5] Full-file bitmaps are downsampled to the producer's
    // column budget instead of one column per FFT frame (~13k for 5 min).
    val bitmapWidth =
        if ((isWavAnalyzerMode || isReportModeActive) && history.isNotEmpty()) {
            SpectrogramImageProducer.columnsFor(history.size)
        } else {
            historySize
        }
    val bitmapHeight = displayedBinCount

    var cursorYRatio by remember { mutableFloatStateOf(0.5f) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isReportModeActive) {
        if (!isReportModeActive) {
            zoom = 1f
            pan = Offset.Zero
        }
    }

    // Animation de clignotement / pulsation pour le détecteur d'émergence
    val infiniteTransition = rememberInfiniteTransition(label = "beaconPulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulsePhase",
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseRadius",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    val effectiveMin = if (displayMode == DisplayMode.TTNR) 1.0 else minDb
    val effectiveMax = if (displayMode == DisplayMode.TTNR) 20.0 else maxDb

    // [P1/U2, plan 3.5] Pixels are produced OFF the main thread and delivered
    // as alternating double-buffered bitmaps: every data change repaints,
    // without per-frame allocation. (The old code mutated one remembered
    // bitmap in a LaunchedEffect and relied on an unrelated recomposition to
    // repaint — the "black spectrogram until first interaction" quirk.)
    val producer =
        remember(bitmapWidth, bitmapHeight) {
            SpectrogramImageProducer(bitmapWidth, bitmapHeight)
        }
    val imageBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        history,
        effectiveMin,
        effectiveMax,
        displayMode,
        isWavAnalyzerMode,
        isReportModeActive,
        minBin,
        maxBin,
        producer,
    ) {
        if (history.isNotEmpty()) {
            val isTtnr = displayMode == DisplayMode.TTNR
            // [D7, plan 3.7] Sub-30 Hz floor is applied here, at the display layer.
            val maskBelowBin = Math.ceil(AudioConfig.DISPLAY_MIN_FREQ_HZ * totalBinCount / nyquistFreq).toInt()
            value =
                kotlinx.coroutines
                    .withContext(kotlinx.coroutines.Dispatchers.Default) {
                        if (isWavAnalyzerMode || isReportModeActive) {
                            producer.renderFull(history, minBin, maxBin, effectiveMin, effectiveMax, isTtnr, maskBelowBin)
                        } else {
                            // [plan 3.4] Chronological history: the newest frame is LAST.
                            producer.appendLatest(history.last(), minBin, maxBin, effectiveMin, effectiveMax, isTtnr, maskBelowBin)
                        }
                    }.asImageBitmap()
        }
    }

    // [U3, P2] Paints hoisted out of the 43 Hz draw loop AND sized from dp/sp: canvas text
    // ignored the user's font scale entirely while every other label honoured it.
    val textPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE
                textSize = dimens.labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    val tickPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE
                strokeWidth = dimens.hairline
                isAntiAlias = true
            }
        }

    // Peinture très visible pour le curseur (ligne blanche avec ombre noire)
    val cursorLinePaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE // Blanc pur
                style = Paint.Style.STROKE
                strokeWidth = dimens.cursorStroke
                pathEffect = DashPathEffect(floatArrayOf(dimens.markerRadius * 4f, dimens.markerRadius * 2.5f), 0f)
                setShadowLayer(dimens.hairline * 4f, 0f, 0f, AndroidColor.BLACK)
                isAntiAlias = true
            }
        }

    // Peinture pour la courbe H1 (Violet vif, trait épais, légèrement transparent)
    val h1LinePaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.parseColor("#D500F9")
                alpha = 178 // ~70% opaque (30% transparent)
                style = Paint.Style.STROKE
                strokeWidth = dimens.traceStroke * 2f
                pathEffect = DashPathEffect(floatArrayOf(dimens.markerRadius * 5f, dimens.markerRadius * 3.5f), 0f)
                isAntiAlias = true
            }
        }

    val cursorBadgeBgPaint =
        remember {
            Paint().apply {
                color = AndroidColor.parseColor("#E6002A36") // Cyan très sombre translucide
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }

    val cursorBadgeTextPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.parseColor("#00E5FF")
                textSize = dimens.badgeTextSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    // Peintures pour les Balises d'Émergence
    val beaconPulsePaint =
        remember(dimens) {
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = dimens.traceStroke
                isAntiAlias = true
            }
        }

    val beaconCenterPaint =
        remember {
            Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }

    val beaconBadgeBgPaint =
        remember {
            Paint().apply {
                color = AndroidColor.parseColor("#EE1A1A2E") // Sombre translucide
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }

    val beaconBadgeTextPaint =
        remember(dimens) {
            Paint().apply {
                textSize = dimens.tagTextSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    val filterFillPaint =
        remember {
            Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
            }
        }
    val filterStrokePaint =
        remember(dimens) {
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = dimens.traceStroke * 2f
                isAntiAlias = true
            }
        }
    val manualTagBgPaint =
        remember {
            Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
    val manualTagTextPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE
                textSize = dimens.tagTextSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }
    val manualTagLeaderPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE
                strokeWidth = dimens.hairline
                isAntiAlias = true
                alpha = 150
            }
        }
    val harmonicTagBgPaint =
        remember {
            Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
    val harmonicTagBorderPaint =
        remember(dimens) {
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = dimens.hairline
                isAntiAlias = true
            }
        }
    val harmonicTagTextPaint =
        remember(dimens) {
            Paint().apply {
                color = AndroidColor.WHITE
                textSize = dimens.tagTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
        }

    // --- DÉTECTION DES PICS D'ÉMERGENCE SUR LA TRAME COURANTE ---
    val detectedPeaks =
        remember(absHistory, ttnrHistory, isDetectorEnabled, emergenceThresholdDb, magnitudeGateDbFS, minBin, maxBin) {
            val peaksList = mutableListOf<EmergencePeak>()
            if (isDetectorEnabled && absHistory.isNotEmpty() && ttnrHistory.isNotEmpty()) {
                // [plan 3.4] Chronological history: the latest frame is LAST.
                val latestAbs = absHistory.last()
                val latestTtnr = ttnrHistory.last()
                val startBin = maxOf(1, minBin)
                val endBin = minOf(latestAbs.size - 1, latestTtnr.size - 1, maxBin)

                for (i in startBin until endBin) {
                    val ttnr = latestTtnr[i].toDouble()
                    val absVal = latestAbs[i].toDouble()
                    val freqHz = (i * nyquistFreq).toDouble() / totalBinCount

                    val reqThreshold =
                        when {
                            freqHz < 1500.0 -> maxOf(emergenceThresholdDb, 4.2)
                            freqHz < 4000.0 -> maxOf(emergenceThresholdDb, 3.2)
                            else -> emergenceThresholdDb
                        }

                    val reqGate =
                        when {
                            freqHz < 500.0 -> maxOf(magnitudeGateDbFS, -75.0)
                            freqHz < 3000.0 -> maxOf(magnitudeGateDbFS, -85.0)
                            else -> magnitudeGateDbFS
                        }

                    val prevTtnr = latestTtnr[i - 1].toDouble()
                    val nextTtnr = latestTtnr[i + 1].toDouble()

                    // Validation Pic Structuré NVH (anti-spikes isolés de 1 pixel)
                    if (ttnr >= reqThreshold && absVal >= reqGate && (prevTtnr > 0.5 || nextTtnr > 0.5)) {
                        if (ttnr >= prevTtnr && ttnr >= nextTtnr) {
                            peaksList.add(EmergencePeak(i, freqHz.toInt(), ttnr, absVal))
                        }
                    }
                }

                // Non-Maximum Suppression NVH v7 : Trier par TTNR décroissant et éliminer les doublons proches (< 4 bins)
                peaksList.sortByDescending { it.ttnrDb }
                val filteredPeaks = mutableListOf<EmergencePeak>()
                for (p in peaksList) {
                    if (filteredPeaks.none { Math.abs(it.binIndex - p.binIndex) < 4 }) {
                        filteredPeaks.add(p)
                    }
                    if (filteredPeaks.size >= 12) break // Retenir au maximum les 12 plus fortes émergences (ordres + sifflements HF)
                }
                filteredPeaks
            } else {
                emptyList()
            }
        }

    // [§12, plan 4.4] The canvas is the app's central measurement surface and exposed NO
    // semantics at all — a screen-reader user got silence where the numbers are. It cannot
    // speak a spectrogram, but it can speak what an operator would read off it: the strongest
    // line right now, and the axis ranges.
    val peak =
        remember(history, minBin, maxBin, totalBinCount) {
            val latest = history.lastOrNull()
            if (latest == null || totalBinCount <= 0) {
                null
            } else {
                var bestBin = minBin
                var bestVal = Double.NEGATIVE_INFINITY
                for (b in minBin until maxBin) {
                    val v = latest.getOrNull(b)?.toDouble() ?: continue
                    if (v > bestVal) {
                        bestVal = v
                        bestBin = b
                    }
                }
                ((bestBin.toLong() * nyquistFreq) / totalBinCount).toInt() to bestVal
            }
        }
    // Draw-scope labels: the raw patterns are read in composition; only String.format runs
    // per frame [lint LocalContextGetResourceValueCall].
    val tagLabelFormat = stringResource(R.string.tag_order_with_level)
    val timeAxisTitle = stringResource(R.string.axis_time)
    val peakSummary =
        if (peak == null) {
            stringResource(R.string.cd_spectrogram_empty)
        } else {
            stringResource(R.string.cd_spectrogram, actualMinFreq, actualMaxFreq, peak.first, peak.second)
        }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { contentDescription = peakSummary }
                .pointerInput(isReportModeActive, isDrawingMode, bitmapWidth, bitmapHeight, dimens) {
                    if (isReportModeActive && isDrawingMode) {
                        detectTapGestures { offset ->
                            // [U3] The SAME geometry the draw pass uses — touch and paint can
                            // no longer disagree about where the plot is.
                            val geo = dimens.spectroGeometry(size.width.toFloat(), size.height.toFloat(), zoom, pan)
                            if (!geo.isUsable) return@detectTapGestures
                            // modifier.pointerInput is AFTER graphicsLayer, so the framework already inverse-transforms the offset.
                            if (!geo.containsPlot(offset.x, offset.y)) return@detectTapGestures

                            val bitmapX = geo.fractionForX(offset.x) * bitmapWidth
                            val bitmapY = geo.fractionForY(offset.y) * bitmapHeight

                            val numFrames = history.size
                            val frameIndex =
                                if (bitmapWidth > 0) {
                                    ((bitmapX / bitmapWidth) * (numFrames - 1)).toInt().coerceIn(0, numFrames - 1)
                                } else {
                                    0
                                }

                            val displayedBins = (maxBin - minBin).coerceAtLeast(1)
                            val binIndex =
                                (maxBin - 1) - if (bitmapHeight > 0) ((bitmapY / bitmapHeight) * (displayedBins - 1)).toInt() else 0

                            onAddManualPoint(frameIndex, binIndex.coerceIn(minBin, maxBin - 1))
                        }
                    } else {
                        detectTransformGestures { centroid, panChange, zoomChange, _ ->
                            val geo = dimens.spectroGeometry(size.width.toFloat(), size.height.toFloat(), zoom, pan)
                            if (!geo.isUsable) return@detectTransformGestures

                            // [U4] One transform for zoom+pan, shared with the draw pass and
                            // unit-tested in PlotGeometryTest.
                            val next = geo.zoomedAround(zoomChange, centroid.x, centroid.y, panChange.x, panChange.y)
                            zoom = next.zoom
                            pan =
                                androidx.compose.ui.geometry
                                    .Offset(next.panXPx, next.panYPx)
                        }
                    }
                }.pointerInput(isDrawingMode, dimens) {
                    if (!isDrawingMode) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.pressed }
                                val geo = dimens.spectroGeometry(size.width.toFloat(), size.height.toFloat(), zoom, pan)
                                // The frequency cursor is dragged from the left gutter.
                                if (pointer != null && pointer.position.x < geo.left && geo.plotHeight > 0) {
                                    val relativeY = (pointer.position.y - geo.top).coerceIn(0f, geo.plotHeight)
                                    cursorYRatio = relativeY / geo.plotHeight
                                    pointer.consume()
                                }
                            }
                        }
                    }
                },
    ) {
        val w = size.width
        val h = size.height

        val geo = dimens.spectroGeometry(w, h, zoom, pan)
        val marginLeft = geo.left
        val marginTop = geo.top
        val plotWidth = geo.plotWidth
        val plotHeight = geo.plotHeight

        // [U4, plan 4.2] Every overlay places itself through the SAME transform the bitmap
        // crop uses. Before this, zooming cropped the image but left the playhead, beacons,
        // tags and the H1 curve in unzoomed coordinates — they detached from the measurement
        // the instant a user pinched.
        fun yForBin(binIndex: Float): Float {
            val binFraction = (binIndex - minBin) / displayedBinCount.toFloat().coerceAtLeast(1f)
            return geo.yForFraction(1f - binFraction)
        }

        fun yForFreq(freqHz: Float): Float {
            val span = (actualMaxFreq - actualMinFreq).toFloat()
            if (span <= 0f) return geo.top
            return geo.yForFraction(((actualMaxFreq - freqHz) / span).coerceIn(0f, 1f))
        }

        fun xForFrame(
            frameIndex: Float,
            frameCount: Int,
        ): Float = geo.xForFraction(if (frameCount > 1) frameIndex / (frameCount - 1) else 0f)

        // 1. Dessiner le spectrogramme
        // Calculate the visible portion of the bitmap based on zoom and pan
        val srcX = (-pan.x / (plotWidth * zoom) * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
        val srcY = (-pan.y / (plotHeight * zoom) * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
        val srcW = (bitmapWidth / zoom).toInt().coerceIn(1, bitmapWidth - srcX)
        val srcH = (bitmapHeight / zoom).toInt().coerceIn(1, bitmapHeight - srcY)

        imageBitmap?.let { img ->
            drawImage(
                image = img,
                srcOffset =
                    androidx.compose.ui.unit
                        .IntOffset(srcX, srcY),
                srcSize =
                    androidx.compose.ui.unit
                        .IntSize(srcW, srcH),
                dstOffset =
                    androidx.compose.ui.unit
                        .IntOffset(marginLeft.toInt(), marginTop.toInt()),
                dstSize =
                    androidx.compose.ui.unit
                        .IntSize(plotWidth.toInt(), plotHeight.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
            )
        }

        // 1b. Curseur temporel de lecture en mode Analyseur WAV
        if (isWavAnalyzerMode) {
            // [U4] Zoom-aware: the playhead tracks the image, and is hidden when the moment
            // it marks is scrolled outside the zoomed viewport.
            val xCursor = geo.xForFraction(wavPlaybackProgress.coerceIn(0f, 1f))
            if (geo.containsX(xCursor)) {
                drawLine(
                    color = NvhModeWavAccent,
                    start = Offset(xCursor, geo.top),
                    end = Offset(xCursor, geo.bottom),
                    strokeWidth = dimens.cursorStroke,
                )
                drawCircle(
                    color = NvhModeWavAccent,
                    radius = dimens.markerRadius,
                    center = Offset(xCursor, geo.top),
                )
            }
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            val plotBottom = marginTop + plotHeight
            val plotRight = marginLeft + plotWidth

            // --- AXE Y (Fréquences) ---
            native.drawLine(marginLeft, marginTop, marginLeft, plotBottom, tickPaint)
            val topViewFraction = (-pan.y) / (plotHeight * zoom)
            val bottomViewFraction = (-pan.y + plotHeight) / (plotHeight * zoom)

            val visibleMaxFreq = actualMaxFreq - topViewFraction * (actualMaxFreq - actualMinFreq)
            val visibleMinFreq = actualMaxFreq - bottomViewFraction * (actualMaxFreq - actualMinFreq)

            val ySteps = 5
            for (i in 0..ySteps) {
                val fraction = i.toFloat() / ySteps
                val f = (visibleMaxFreq - fraction * (visibleMaxFreq - visibleMinFreq)).toInt()
                val y = marginTop + fraction * plotHeight

                native.drawLine(marginLeft - 15f, y, marginLeft, y, tickPaint)

                val textY =
                    when (i) {
                        0 -> y + 25f
                        ySteps -> y - 5f
                        else -> y + 10f
                    }
                native.drawText("$f Hz", 10f, textY, textPaint)
            }

            // --- DESSIN DES OVERLAYS DE FILTRES AUDIO (Bandes Semi-Transparentes) ---
            if (activeFilters.isNotEmpty()) {
                for (filter in activeFilters) {
                    val baseColor =
                        android.graphics.Color.argb(
                            (filter.color.alpha * 255).toInt(),
                            (filter.color.red * 255).toInt(),
                            (filter.color.green * 255).toInt(),
                            (filter.color.blue * 255).toInt(),
                        )

                    filterFillPaint.color = baseColor
                    filterFillPaint.alpha = 80 // Plus subtil, la lumière SCREEN fait le reste

                    filterStrokePaint.color = baseColor
                    filterStrokePaint.alpha = 255
                    // Glow effect sur la bordure (Néon)
                    filterStrokePaint.setShadowLayer(8f, 0f, 0f, baseColor)

                    val fMin = filter.minFreq.toFloat()
                    val fMax = filter.maxFreq.toFloat()

                    // Fonction locale pour calculer la coordonnée Y d'une fréquence
                    // [U4] Filter bands follow the zoomed frequency axis.
                    fun getFreqY(freqHz: Float): Float = yForFreq(freqHz)

                    fun drawFilterBand(
                        yTopRaw: Float,
                        yBottomRaw: Float,
                    ) {
                        // Clip to the viewport so a band whose edge is off-screen still
                        // shades the part of the spectrum that IS visible.
                        val yTop = yTopRaw.coerceIn(geo.top, geo.bottom)
                        val yBottom = yBottomRaw.coerceIn(geo.top, geo.bottom)
                        if (yBottom > yTop) {
                            native.drawRect(marginLeft, yTop, plotRight, yBottom, filterFillPaint)
                            if (yTopRaw >= geo.top) native.drawLine(marginLeft, yTop, plotRight, yTop, filterStrokePaint)
                            if (yBottomRaw <= geo.bottom) {
                                native.drawLine(marginLeft, yBottom, plotRight, yBottom, filterStrokePaint)
                            }
                        }
                    }

                    when (filter.type) {
                        FilterType.LOW_PASS -> {
                            val yTop = marginTop
                            val yBottom = getFreqY(fMax)
                            drawFilterBand(yTop, yBottom)
                        }
                        FilterType.HIGH_PASS -> {
                            val yTop = getFreqY(fMin)
                            val yBottom = plotBottom
                            drawFilterBand(yTop, yBottom)
                        }
                        FilterType.BAND_PASS -> {
                            // En passe-bande, on rejette l'extérieur.
                            val yTop1 = marginTop
                            val yBottom1 = getFreqY(fMax)
                            drawFilterBand(yTop1, yBottom1)

                            val yTop2 = getFreqY(fMin)
                            val yBottom2 = plotBottom
                            drawFilterBand(yTop2, yBottom2)
                        }
                        FilterType.BAND_STOP -> {
                            val yTop = getFreqY(fMax)
                            val yBottom = getFreqY(fMin)
                            drawFilterBand(yTop, yBottom)
                        }
                    }
                }
            }

            // --- DESSIN DES BALISES CLIGNOTANTES D'ÉMERGENCE (Option A: LED Pulsante BORD DROIT pure sans texte) ---
            if (isDetectorEnabled && detectedPeaks.isNotEmpty()) {
                for (peak in detectedPeaks) {
                    // [U4] Beacons sit at the frequency they were detected at, in the zoomed
                    // axis; one outside the viewport is not drawn rather than being pinned to
                    // an edge where it would point at the wrong frequency.
                    val peakY = yForBin(peak.binIndex.toFloat())
                    if (!geo.containsY(peakY)) continue

                    // Couleur : Rouge Néon si TTNR >= 6.0 dB, Jaune/Ambre si TTNR < 6.0 dB
                    val isCritical = peak.ttnrDb >= 6.0
                    val baseColor = if (isCritical) AndroidColor.parseColor("#FF1744") else AndroidColor.parseColor("#FFC107")

                    // Rayon et Alpha pulsants
                    val pulseRadius = dimens.markerRadius * (2.5f + pulsePhase * 3f)
                    val alphaPulse = (230 - pulsePhase * 150).toInt().coerceIn(40, 255)

                    beaconPulsePaint.color = baseColor
                    beaconPulsePaint.alpha = alphaPulse
                    beaconCenterPaint.color = baseColor
                    beaconCenterPaint.alpha = 255

                    // Position X : bord droit extrême
                    val beaconX = plotRight - dimens.markerRadius * 1.5f

                    // 1. Halo pulsant extérieur (LED Aura)
                    native.drawCircle(beaconX, peakY, pulseRadius, beaconPulsePaint)
                    // 2. Centre lumineux solide
                    native.drawCircle(beaconX, peakY, dimens.markerRadius * 1.5f, beaconCenterPaint)
                }
            }

            // --- DESSIN DU CALQUE H1 (Pointillé Cyan Fluo) ---

            // --- MANUAL SMART TRACKING OVERLAYS ---
            if (isReportModeActive) {
                fun mapAnchorToScreen(anchor: ManualOrderAnchor): Offset =
                    Offset(
                        xForFrame(anchor.frameIndex.toFloat(), history.size),
                        geo.yForFraction(
                            (maxBin - 1 - anchor.exactBinF) / (displayedBinCount - 1).coerceAtLeast(1).toFloat(),
                        ),
                    )

                // On s'assure que le dessin est clippé à la zone du plot !
                clipRect(
                    left = marginLeft,
                    top = marginTop,
                    right = plotRight,
                    bottom = plotBottom,
                ) {
                    // 1. Draw Validated Orders
                    manualTrackedOrders.forEach { order ->
                        val isSelected = order == selectedManualOrder || isBrillanceModeEnabled
                        val path =
                            androidx.compose.ui.graphics
                                .Path()
                        var isFirst = true
                        order.path.forEach { anchor ->
                            val pt = mapAnchorToScreen(anchor)
                            if (isFirst) {
                                path.moveTo(pt.x, pt.y)
                                isFirst = false
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        }
                        if (isSelected) {
                            // Halo de surbrillance
                            drawPath(
                                path = path,
                                color = order.color.copy(alpha = 0.6f),
                                style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = dimens.traceStroke * 4f,
                                        pathEffect =
                                            androidx.compose.ui.graphics.PathEffect
                                                .cornerPathEffect(dimens.markerRadius * 3f),
                                    ),
                            )
                            // Ligne centrale surbrillance
                            drawPath(
                                path = path,
                                color = order.color,
                                style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = dimens.traceStroke * 1.6f,
                                        pathEffect =
                                            androidx.compose.ui.graphics.PathEffect
                                                .cornerPathEffect(dimens.markerRadius * 3f),
                                    ),
                            )
                        } else {
                            // Contour noir subtil pour garantir le contraste sur fond clair/rouge/jaune
                            drawPath(
                                path = path,
                                color = NvhCanvas.copy(alpha = 0.5f),
                                style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = dimens.traceStroke * 1.5f,
                                        pathEffect =
                                            androidx.compose.ui.graphics.PathEffect
                                                .cornerPathEffect(dimens.markerRadius * 3f),
                                    ),
                            )
                            // Ligne centrale de couleur, épaisseur moyenne, semi-transparente
                            drawPath(
                                path = path,
                                color = order.color.copy(alpha = 0.6f),
                                style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = dimens.traceStroke,
                                        pathEffect =
                                            androidx.compose.ui.graphics.PathEffect
                                                .cornerPathEffect(dimens.markerRadius * 3f),
                                    ),
                            )
                        }
                    }

                    // Draw manual tags
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val bgPaint = manualTagBgPaint
                        val textPaint = manualTagTextPaint

                        val occupiedRects = mutableListOf<android.graphics.RectF>()

                        manualTrackedOrders.forEach { order ->
                            val isSelected = order == selectedManualOrder || isBrillanceModeEnabled
                            if (isSelected && order.path.isNotEmpty()) {
                                val lastAnchor = order.path.last()
                                val pt = mapAnchorToScreen(lastAnchor)
                                val text = order.name
                                val textWidth = textPaint.measureText(text)
                                val paddingX = dimens.tagTextSize * 0.6f
                                val paddingY = dimens.tagTextSize * 0.35f

                                val boxWidth = textWidth + paddingX * 2
                                val boxHeight = dimens.tagTextSize * 1.3f + paddingY * 2

                                var tx = pt.x + dimens.markerRadius * 4f
                                var ty = pt.y
                                if (tx + boxWidth > plotRight) {
                                    tx = pt.x - boxWidth - 15f
                                }

                                var currentRect = android.graphics.RectF(tx, ty - 25f - paddingY, tx + boxWidth, ty + 10f + paddingY)
                                var offsetStep = 0
                                val maxSteps = 50

                                // Prevent collision with other labels
                                while (offsetStep < maxSteps) {
                                    var collision = false
                                    for (r in occupiedRects) {
                                        val expandedR = android.graphics.RectF(r.left - 6f, r.top - 6f, r.right + 6f, r.bottom + 6f)
                                        if (android.graphics.RectF.intersects(currentRect, expandedR)) {
                                            collision = true
                                            break
                                        }
                                    }
                                    if (!collision) break

                                    offsetStep++
                                    val direction = if (offsetStep % 2 == 1) -1 else 1
                                    val shift = ((offsetStep + 1) / 2) * dimens.tagTextSize * direction

                                    val newTy = ty + shift
                                    currentRect = android.graphics.RectF(tx, newTy - 25f - paddingY, tx + boxWidth, newTy + 10f + paddingY)
                                }

                                // Prevent collision with screen bounds
                                if (currentRect.top < marginTop) {
                                    val diff = marginTop - currentRect.top + 10f
                                    currentRect.offset(0f, diff)
                                }
                                if (currentRect.bottom > plotBottom) {
                                    val diff = currentRect.bottom - plotBottom - 10f
                                    currentRect.offset(0f, diff)
                                }

                                // Double check bounds might have pushed it back into collision, but we prioritize being in view
                                occupiedRects.add(currentRect)

                                val orderColorInt =
                                    android.graphics.Color.argb(
                                        (0.8f * 255).toInt(),
                                        (order.color.red * 255).toInt(),
                                        (order.color.green * 255).toInt(),
                                        (order.color.blue * 255).toInt(),
                                    )
                                bgPaint.color = orderColorInt

                                nativeCanvas.drawRoundRect(currentRect, dimens.markerRadius, dimens.markerRadius, bgPaint)

                                bgPaint.style = android.graphics.Paint.Style.STROKE
                                bgPaint.strokeWidth = dimens.hairline
                                bgPaint.color = android.graphics.Color.WHITE
                                nativeCanvas.drawRoundRect(currentRect, dimens.markerRadius, dimens.markerRadius, bgPaint)
                                bgPaint.style = android.graphics.Paint.Style.FILL

                                val distY = Math.abs(currentRect.centerY() - pt.y)
                                if (distY > dimens.tagTextSize) {
                                    val boxEdgeX = if (tx > pt.x) currentRect.left else currentRect.right
                                    nativeCanvas.drawLine(pt.x, pt.y, boxEdgeX, currentRect.centerY(), manualTagLeaderPaint)
                                }

                                nativeCanvas.drawText(text, currentRect.left + paddingX, currentRect.bottom - paddingY - 2f, textPaint)
                            }
                        }
                    }

                    // 2. Draw Current Smart Path
                    if (currentSmartPath.isNotEmpty()) {
                        val path =
                            androidx.compose.ui.graphics
                                .Path()
                        var isFirst = true
                        currentSmartPath.forEach { anchor ->
                            val pt = mapAnchorToScreen(anchor)
                            if (isFirst) {
                                path.moveTo(pt.x, pt.y)
                                isFirst = false
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = NvhOnSurface.copy(alpha = 0.9f),
                            style =
                                androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = dimens.traceStroke,
                                    pathEffect =
                                        androidx.compose.ui.graphics.PathEffect
                                            .dashPathEffect(
                                                floatArrayOf(dimens.markerRadius * 3f, dimens.markerRadius * 3f),
                                                0f,
                                            ),
                                ),
                        )
                    }

                    // 3. Draw Current User Anchor Points
                    currentUserPoints.forEach { anchor ->
                        val pt = mapAnchorToScreen(anchor)
                        // Halo clignotant
                        drawCircle(
                            color = NvhStatusBad.copy(alpha = pulseAlpha),
                            radius = pulseRadius,
                            center = pt,
                        )
                        // Bordure extérieure noire pour le contraste
                        drawCircle(
                            color = NvhCanvas,
                            radius = dimens.markerRadius * 1.8f,
                            center = pt,
                        )
                        // Point rouge central (plus grand)
                        drawCircle(
                            color = NvhStatusBad,
                            radius = dimens.markerRadius * 1.5f,
                            center = pt,
                        )
                        // Cœur blanc
                        drawCircle(
                            color = NvhOnSurface,
                            radius = dimens.markerRadius * 0.9f,
                            center = pt,
                        )
                    }
                }
            }

            if (showH1Overlay && kinematicsConfig.isEnabled && telemetryHistory.isNotEmpty()) {
                val path = android.graphics.Path()
                var isFirst = true
                val numFrames = telemetryHistory.size

                for (x in 0 until plotWidth.toInt()) {
                    // [plan 3.4] One chronological mapping for every mode:
                    // left = oldest, right = newest.
                    // [U4] The screen column is converted to a DATA fraction through the
                    // shared transform, so the curve stays on its own trace when zoomed —
                    // it used to be drawn against the unzoomed axis.
                    val xPos = marginLeft + x
                    val dataFraction = geo.fractionForX(xPos)
                    if (dataFraction < 0f || dataFraction > 1f) {
                        isFirst = true
                        continue
                    }
                    val exactIdx = dataFraction * (numFrames - 1)
                    val idxBefore = exactIdx.toInt().coerceIn(0, numFrames - 1)
                    val idxAfter = (idxBefore + 1).coerceIn(0, numFrames - 1)
                    val fraction = exactIdx - idxBefore

                    val telemBefore = telemetryHistory[idxBefore]
                    val telemAfter = telemetryHistory[idxAfter]

                    val speedBefore =
                        if (kinematicsConfig.isEnabled &&
                            telemBefore.theoreticalSpeedKmh > 0.1f
                        ) {
                            telemBefore.theoreticalSpeedKmh
                        } else {
                            telemBefore.speedKmh
                        }
                    val speedAfter =
                        if (kinematicsConfig.isEnabled &&
                            telemAfter.theoreticalSpeedKmh > 0.1f
                        ) {
                            telemAfter.theoreticalSpeedKmh
                        } else {
                            telemAfter.speedKmh
                        }

                    val speed = speedBefore + fraction * (speedAfter - speedBefore)

                    if (speed > 1.0f) {
                        val h1Freq = kinematicsConfig.calculateH1FreqHz(speed)
                        val projectedFreq = h1Freq * projectedOrder
                        val y = yForFreq(projectedFreq.toFloat())
                        if (projectedFreq >= actualMinFreq && projectedFreq <= actualMaxFreq && geo.containsY(y)) {
                            if (isFirst) {
                                path.moveTo(xPos, y)
                                isFirst = false
                            } else {
                                path.lineTo(xPos, y)
                            }
                        } else {
                            isFirst = true
                        }
                    } else {
                        isFirst = true
                    }
                }

                native.drawPath(path, h1LinePaint)
            }

            // --- DESSIN DES ÉTIQUETTES D'HARMONIQUES (H_k) AVEC RÉMANENCE VISUELLE ---
            if (kinematicsConfig.isEnabled && trackedHarmonicTags.isNotEmpty()) {
                val tagBgPaint = harmonicTagBgPaint
                val tagBorderPaint = harmonicTagBorderPaint
                val tagTextPaint = harmonicTagTextPaint

                val nowMs = System.currentTimeMillis()
                val maxHoldMs = (kinematicsConfig.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)

                for (tag in trackedHarmonicTags) {
                    val ageMs = nowMs - tag.lastSeenTimestampMs
                    val alphaRatio = (1f - (ageMs.toFloat() / maxHoldMs.toFloat())).coerceIn(0f, 1f)
                    if (alphaRatio <= 0f) continue

                    val binFraction = (tag.binIndex - minBin).toFloat() / displayedBinCount.coerceAtLeast(1)
                    if (binFraction !in 0f..1f) continue

                    // [U4] Tags follow the zoomed frequency axis; one whose harmonic is
                    // scrolled out of view is dropped rather than parked at an edge, where it
                    // would label a frequency that is not there.
                    val basePeakY = yForBin(tag.binIndex.toFloat())
                    if (!geo.containsY(basePeakY)) continue
                    val peakY = basePeakY.coerceIn(marginTop, plotBottom - dimens.tagTextSize)

                    val isCritical = tag.ttnrDb >= 6.0
                    val primaryColor = if (isCritical) AndroidColor.parseColor("#FF1744") else AndroidColor.parseColor("#FFC107")

                    val alphaInt = (alphaRatio * 255).toInt().coerceIn(0, 255)
                    tagBgPaint.color = AndroidColor.parseColor("#E6121212")
                    tagBgPaint.alpha = (alphaRatio * 230).toInt()

                    tagBorderPaint.color = primaryColor
                    tagBorderPaint.alpha = alphaInt

                    tagTextPaint.color = primaryColor
                    tagTextPaint.alpha = alphaInt

                    val label = String.format(java.util.Locale.getDefault(), tagLabelFormat, tag.orderName, tag.ttnrDb)
                    val textWidth = tagTextPaint.measureText(label)
                    val padH = dimens.tagTextSize * 0.5f
                    val badgeH = dimens.tagTextSize * 1.9f
                    val badgeW = textWidth + padH * 2f
                    val corner = dimens.markerRadius

                    // Position X : bord droit décalé
                    val tagX = plotRight - badgeW - dimens.markerRadius * 2f
                    val tagYTop = (peakY - badgeH / 2f).coerceIn(marginTop, plotBottom - badgeH)

                    // Dessin du badge d'harmonique
                    native.drawRoundRect(tagX, tagYTop, tagX + badgeW, tagYTop + badgeH, corner, corner, tagBgPaint)
                    native.drawRoundRect(tagX, tagYTop, tagX + badgeW, tagYTop + badgeH, corner, corner, tagBorderPaint)
                    native.drawText(label, tagX + padH, tagYTop + badgeH * 0.7f, tagTextPaint)
                }
            }

            if (!isDrawingMode) {
                // --- CURSEUR EN FRÉQUENCE DISCRET ---
                val cursorY = marginTop + cursorYRatio * plotHeight

                // Utilisation des fréquences visibles calculées pour l'axe Y
                val selectedFreqHz = visibleMinFreq + ((1f - cursorYRatio) * (visibleMaxFreq - visibleMinFreq))

                native.drawLine(marginLeft, cursorY, plotRight, cursorY, cursorLinePaint)

                val freqStr = String.format(java.util.Locale.US, "%.1f Hz", selectedFreqHz)
                val badgeTextWidth = cursorBadgeTextPaint.measureText(freqStr)
                val badgePaddingHorizontal = dimens.badgeTextSize * 0.5f
                val badgeHeight = dimens.badgeTextSize * 2f
                val corner = dimens.markerRadius

                val badgeLeft = marginLeft + dimens.markerRadius * 2f
                val badgeTop = (cursorY - badgeHeight / 2f).coerceIn(marginTop, plotBottom - badgeHeight)
                val badgeRight = badgeLeft + badgeTextWidth + (badgePaddingHorizontal * 2f)
                val badgeBottom = badgeTop + badgeHeight

                native.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, corner, corner, cursorBadgeBgPaint)
                native.drawText(freqStr, badgeLeft + badgePaddingHorizontal, badgeTop + badgeHeight * 0.72f, cursorBadgeTextPaint)
            }

            // --- AXE X (Temps en secondes) ---
            native.drawLine(marginLeft, plotBottom, plotRight, plotBottom, tickPaint)

            val hopSize = fftSize / 2.0
            val dt = hopSize / sampleRate
            val totalTimeSec =
                if ((isWavAnalyzerMode || isReportModeActive) &&
                    history.isNotEmpty()
                ) {
                    (history.size * dt)
                } else {
                    (historySize * dt)
                }

            val visibleMinTimeSec = if (isReportModeActive) (-pan.x / (plotWidth * zoom)) * totalTimeSec else (if (isWavAnalyzerMode) 0.0 else -totalTimeSec)
            val visibleMaxTimeSec = if (isReportModeActive) ((-pan.x + plotWidth) / (plotWidth * zoom)) * totalTimeSec else (if (isWavAnalyzerMode) totalTimeSec else 0.0)

            val xSteps = 5
            for (i in 0..xSteps) {
                val fraction = i.toFloat() / xSteps
                val x = marginLeft + fraction * plotWidth
                val tSec = visibleMinTimeSec + fraction * (visibleMaxTimeSec - visibleMinTimeSec)

                native.drawLine(x, plotBottom, x, plotBottom + 15f, tickPaint)

                val label = String.format("%.1fs", tSec)
                val labelWidth = textPaint.measureText(label)
                val textX = (x - labelWidth / 2f).coerceIn(marginLeft, maxOf(marginLeft, plotRight - labelWidth))
                native.drawText(label, textX, plotBottom + 50f, textPaint)
            }

            // Titre Axe X
            val xTitle = timeAxisTitle
            val xTitleWidth = textPaint.measureText(xTitle)
            native.drawText(xTitle, marginLeft + (plotWidth - xTitleWidth) / 2f, h - dimens.labelTextSize * 0.4f, textPaint)

            // --- LÉGENDE (Affichée désormais de manière fluide en UI Compose sous les boutons) ---
        }
    }
}
