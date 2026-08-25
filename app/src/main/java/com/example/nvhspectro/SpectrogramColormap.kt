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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.ManualOrderAnchor
import com.example.nvhspectro.data.SmartTrackedOrder
import com.example.nvhspectro.data.TrackedHarmonicTag
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.FilterType
import kotlin.math.max
import kotlin.math.min

/**
 * Data class représentant un pic d'émergence tonale détecté sur la trame courante
 */
data class EmergencePeak(
    val binIndex: Int,
    val freqHz: Int,
    val ttnrDb: Double,
    val absDbFS: Double
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
        v < 0.125f -> { r = 0f; g = 0f; b = 0.5f + 4f * v }
        v < 0.375f -> { r = 0f; g = 4f * (v - 0.125f); b = 1f }
        v < 0.625f -> { r = 4f * (v - 0.375f); g = 1f; b = 1f - 4f * (v - 0.375f) }
        v < 0.875f -> { r = 1f; g = 1f - 4f * (v - 0.625f); b = 0f }
        else -> { r = 1f - 4f * (v - 0.875f); g = 0f; b = 0f }
    }
    return AndroidColor.argb(
        255,
        (r * 255).toInt(),
        (g * 255).toInt(),
        (b * 255).toInt()
    )
}

@Composable
fun SpectrogramCanvas(
    history: List<DoubleArray>,
    absHistory: List<DoubleArray> = emptyList(),
    ttnrHistory: List<DoubleArray> = emptyList(),
    modifier: Modifier = Modifier,
    minDb: Double = -120.0,
    maxDb: Double = 0.0,
    minFreq: Int = 0,
    maxFreq: Int = 10000,
    fftSize: Int = 2048,
    sampleRate: Int = 44100,
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
    onAddManualPoint: (Int, Int) -> Unit = { _, _ -> }
) {
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

    val bitmapWidth = if ((isWavAnalyzerMode || isReportModeActive) && history.isNotEmpty()) history.size else historySize
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
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePhase"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val bitmap by remember(bitmapWidth, bitmapHeight) {
        mutableStateOf(Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888))
    }
    val pixels by remember(bitmapWidth, bitmapHeight) {
        mutableStateOf(IntArray(bitmapWidth * bitmapHeight) { AndroidColor.BLACK })
    }

    val effectiveMin = if (displayMode == DisplayMode.TTNR) 1.0 else minDb
    val effectiveMax = if (displayMode == DisplayMode.TTNR) 20.0 else maxDb

    LaunchedEffect(history, effectiveMin, effectiveMax, displayMode, isWavAnalyzerMode, isReportModeActive) {
        if (history.isNotEmpty()) {
            if (isWavAnalyzerMode || isReportModeActive) {
                val numFrames = history.size
                for (x in 0 until bitmapWidth) {
                    val frameIdx = (x * (numFrames - 1)) / maxOf(1, bitmapWidth - 1)
                    val frame = if (frameIdx in history.indices) history[frameIdx] else DoubleArray(0)

                    for (y in 0 until bitmapHeight) {
                        val binIndex = (maxBin - 1) - (y * (displayedBinCount - 1)) / maxOf(1, bitmapHeight - 1)
                        val magnitude = if (binIndex in frame.indices) frame[binIndex] else effectiveMin
                        
                        val colorInt = if (displayMode == DisplayMode.TTNR && magnitude < 1.0) {
                            AndroidColor.BLACK
                        } else {
                            val rawNormalized = ((magnitude - effectiveMin) / (effectiveMax - effectiveMin)).coerceIn(0.0, 1.0).toFloat()
                            val normalized = if (displayMode == DisplayMode.TTNR && rawNormalized > 0f) {
                                Math.pow(rawNormalized.toDouble(), 0.65).toFloat()
                            } else {
                                rawNormalized
                            }
                            getJetColorInt(normalized)
                        }
                        
                        pixels[y * bitmapWidth + x] = colorInt
                    }
                }
            } else {
                val latestFrame = history.first()

                for (y in 0 until bitmapHeight) {
                    System.arraycopy(pixels, y * bitmapWidth + 1, pixels, y * bitmapWidth, bitmapWidth - 1)

                    val binIndex = (maxBin - 1) - (y * (displayedBinCount - 1)) / (bitmapHeight - 1)
                    val magnitude = if (binIndex in latestFrame.indices) latestFrame[binIndex] else effectiveMin
                    
                    val colorInt = if (displayMode == DisplayMode.TTNR && magnitude < 1.0) {
                        AndroidColor.BLACK
                    } else {
                        val rawNormalized = ((magnitude - effectiveMin) / (effectiveMax - effectiveMin)).coerceIn(0.0, 1.0).toFloat()
                        val normalized = if (displayMode == DisplayMode.TTNR && rawNormalized > 0f) {
                            Math.pow(rawNormalized.toDouble(), 0.65).toFloat()
                        } else {
                            rawNormalized
                        }
                        getJetColorInt(normalized)
                    }
                    
                    pixels[y * bitmapWidth + (bitmapWidth - 1)] = colorInt
                }
            }
            
            bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
        }
    }

    val imageBitmap = bitmap.asImageBitmap()
    
    val textPaint = remember {
        Paint().apply {
            color = AndroidColor.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val tickPaint = remember {
        Paint().apply {
            color = AndroidColor.WHITE
            strokeWidth = 3f
            isAntiAlias = true
        }
    }

    // Peinture très visible pour le curseur (ligne blanche avec ombre noire)
    val cursorLinePaint = remember {
        Paint().apply {
            color = AndroidColor.WHITE // Blanc pur
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            setShadowLayer(4.0f, 0f, 0f, AndroidColor.BLACK)
            isAntiAlias = true
        }
    }

    // Peinture pour la courbe H1 (Violet vif, trait épais, légèrement transparent)
    val h1LinePaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#D500F9")
            alpha = 178 // ~70% opaque (30% transparent)
            style = Paint.Style.STROKE
            strokeWidth = 5.0f
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
            isAntiAlias = true
        }
    }

    val cursorBadgeBgPaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#E6002A36") // Cyan très sombre translucide
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val cursorBadgeTextPaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#00E5FF")
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    // Peintures pour les Balises d'Émergence
    val beaconPulsePaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
    }

    val beaconCenterPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val beaconBadgeBgPaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#EE1A1A2E") // Sombre translucide
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val beaconBadgeTextPaint = remember {
        Paint().apply {
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    // --- DÉTECTION DES PICS D'ÉMERGENCE SUR LA TRAME COURANTE ---
    val detectedPeaks = remember(absHistory, ttnrHistory, isDetectorEnabled, emergenceThresholdDb, magnitudeGateDbFS, minBin, maxBin) {
        val peaksList = mutableListOf<EmergencePeak>()
        if (isDetectorEnabled && absHistory.isNotEmpty() && ttnrHistory.isNotEmpty()) {
            val latestAbs = absHistory.first()
            val latestTtnr = ttnrHistory.first()
            val startBin = maxOf(1, minBin)
            val endBin = minOf(latestAbs.size - 1, latestTtnr.size - 1, maxBin)

            for (i in startBin until endBin) {
                val ttnr = latestTtnr[i]
                val absVal = latestAbs[i]
                val freqHz = (i * nyquistFreq).toDouble() / totalBinCount

                val reqThreshold = when {
                    freqHz < 1500.0 -> maxOf(emergenceThresholdDb, 4.2)
                    freqHz < 4000.0 -> maxOf(emergenceThresholdDb, 3.2)
                    else -> emergenceThresholdDb
                }

                val reqGate = when {
                    freqHz < 500.0 -> maxOf(magnitudeGateDbFS, -75.0)
                    freqHz < 3000.0 -> maxOf(magnitudeGateDbFS, -85.0)
                    else -> magnitudeGateDbFS
                }

                val prevTtnr = latestTtnr[i - 1]
                val nextTtnr = latestTtnr[i + 1]

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isReportModeActive, isDrawingMode, bitmapWidth, bitmapHeight) {
                if (isReportModeActive && isDrawingMode) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val marginLeft = 150f
                        val marginTop = 60f
                        val marginBottom = 120f
                        val marginRight = 40f
                        val plotWidth = w - marginLeft - marginRight
                        val plotHeight = h - marginTop - marginBottom
                        if (w <= 0 || h <= 0 || plotWidth <= 50f || plotHeight <= 50f) return@detectTapGestures
                        
                        val touchX = offset.x
                        val touchY = offset.y
                        // modifier.pointerInput is AFTER graphicsLayer, so the framework already inverse-transforms the offset.
                        val inverseTouchX = touchX
                        val inverseTouchY = touchY
                        
                        if (inverseTouchX in marginLeft..(w-marginRight) && inverseTouchY in marginTop..(h-marginBottom)) {
                            val x = inverseTouchX - marginLeft
                            val y = inverseTouchY - marginTop
                            
                            val vX = (x - pan.x) / zoom
                            val vY = (y - pan.y) / zoom
                            
                            val bitmapX = if (plotWidth > 0) (vX / plotWidth) * bitmapWidth else 0f
                            val bitmapY = if (plotHeight > 0) (vY / plotHeight) * bitmapHeight else 0f
                            
                            val numFrames = history.size
                            val frameIndex = if (bitmapWidth > 0) ((bitmapX / bitmapWidth) * (numFrames - 1)).toInt().coerceIn(0, numFrames - 1) else 0
                            
                            val displayedBinCount = (maxBin - minBin).coerceAtLeast(1)
                            val binIndex = (maxBin - 1) - if (bitmapHeight > 0) ((bitmapY / bitmapHeight) * (displayedBinCount - 1)).toInt() else 0
                            
                            onAddManualPoint(frameIndex, binIndex.coerceIn(minBin, maxBin - 1))
                        }
                    }
                } else {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val marginLeft = 150f
                        val marginTop = 60f
                        val marginBottom = 120f
                        val marginRight = 40f
                        val plotWidth = w - marginLeft - marginRight
                        val plotHeight = h - marginTop - marginBottom
                        
                        if (w <= 0 || h <= 0 || plotWidth <= 50f || plotHeight <= 50f) return@detectTransformGestures
                        
                        // Zoom on Y axis primarily (frequencies), but we'll zoom uniformly for now
                        val newZoom = (zoom * zoomChange).coerceIn(1f, 20f)
                        
                        // Adjust pan to zoom around the centroid
                        // The pan offset is relative to the top-left of the plot area
                        val plotCentroidX = centroid.x - marginLeft
                        val plotCentroidY = centroid.y - marginTop
                        
                        var newPanX = pan.x * zoomChange + plotCentroidX * (1 - zoomChange) + panChange.x
                        var newPanY = pan.y * zoomChange + plotCentroidY * (1 - zoomChange) + panChange.y
                        
                        // Clamp pan so the image doesn't fly off screen
                        val maxPanX = 0f
                        val minPanX = plotWidth - (plotWidth * newZoom)
                        val maxPanY = 0f
                        val minPanY = plotHeight - (plotHeight * newZoom)
                        
                        newPanX = newPanX.coerceIn(minPanX, maxPanX)
                        newPanY = newPanY.coerceIn(minPanY, maxPanY)
                        
                        zoom = newZoom
                        pan = androidx.compose.ui.geometry.Offset(newPanX, newPanY)
                    }
                }
            }
            .pointerInput(isDrawingMode) {
                if (!isDrawingMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.pressed }
                            if (pointer != null && pointer.position.x < 150f) {
                                val marginTop = 60f
                                val marginBottom = 120f
                                val plotHeight = size.height - marginTop - marginBottom
                                if (plotHeight > 0) {
                                    val relativeY = (pointer.position.y - marginTop).coerceIn(0f, plotHeight)
                                    cursorYRatio = relativeY / plotHeight
                                }
                                pointer.consume()
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        val marginLeft = 150f
        val marginTop = 60f
        val marginBottom = 120f
        val marginRight = 40f
        
        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom

        // 1. Dessiner le spectrogramme
        // Calculate the visible portion of the bitmap based on zoom and pan
        val srcX = (-pan.x / (plotWidth * zoom) * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
        val srcY = (-pan.y / (plotHeight * zoom) * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
        val srcW = (bitmapWidth / zoom).toInt().coerceIn(1, bitmapWidth - srcX)
        val srcH = (bitmapHeight / zoom).toInt().coerceIn(1, bitmapHeight - srcY)

        drawImage(
            image = imageBitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset(srcX, srcY),
            srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
            dstOffset = androidx.compose.ui.unit.IntOffset(marginLeft.toInt(), marginTop.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(plotWidth.toInt(), plotHeight.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None
        )

        // 1b. Curseur temporel de lecture en mode Analyseur WAV
        if (isWavAnalyzerMode) {
            val xCursor = marginLeft + (wavPlaybackProgress.coerceIn(0f, 1f) * plotWidth)
            drawLine(
                color = Color(0xFFF59E0B), // Amber Néon Vif
                start = Offset(xCursor, marginTop),
                end = Offset(xCursor, marginTop + plotHeight),
                strokeWidth = 4f
            )
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = 7f,
                center = Offset(xCursor, marginTop)
            )
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
                
                val textY = when (i) {
                    0 -> y + 25f
                    ySteps -> y - 5f
                    else -> y + 10f
                }
                native.drawText("${f} Hz", 10f, textY, textPaint)
            }


            // --- DESSIN DES OVERLAYS DE FILTRES AUDIO (Bandes Semi-Transparentes) ---
            if (activeFilters.isNotEmpty()) {
                val filterFillPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    // Rendu AAA : Mode SCREEN pour un effet de faisceau lumineux sans griser le fond
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                }
                val filterStrokePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    isAntiAlias = true
                }
                
                for (filter in activeFilters) {
                    val baseColor = android.graphics.Color.argb(
                        (filter.color.alpha * 255).toInt(),
                        (filter.color.red * 255).toInt(),
                        (filter.color.green * 255).toInt(),
                        (filter.color.blue * 255).toInt()
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
                    fun getFreqY(freqHz: Float): Float {
                        if (freqHz <= actualMinFreq) return plotBottom
                        if (freqHz >= actualMaxFreq) return marginTop
                        val fraction = (freqHz - actualMinFreq) / (actualMaxFreq - actualMinFreq)
                        return plotBottom - (fraction * plotHeight)
                    }

                    fun drawFilterBand(yTop: Float, yBottom: Float) {
                        if (yBottom > yTop) {
                            native.drawRect(marginLeft, yTop, plotRight, yBottom, filterFillPaint)
                            native.drawLine(marginLeft, yTop, plotRight, yTop, filterStrokePaint)
                            native.drawLine(marginLeft, yBottom, plotRight, yBottom, filterStrokePaint)
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
                    val binFraction = (peak.binIndex - minBin).toFloat() / displayedBinCount.coerceAtLeast(1)
                    val peakY = marginTop + ((1f - binFraction) * plotHeight).coerceIn(0f, plotHeight)

                    // Couleur : Rouge Néon si TTNR >= 6.0 dB, Jaune/Ambre si TTNR < 6.0 dB
                    val isCritical = peak.ttnrDb >= 6.0
                    val baseColor = if (isCritical) AndroidColor.parseColor("#FF1744") else AndroidColor.parseColor("#FFC107")
                    
                    // Rayon et Alpha pulsants
                    val pulseRadius = 10f + pulsePhase * 14f
                    val alphaPulse = (230 - pulsePhase * 150).toInt().coerceIn(40, 255)
                    
                    beaconPulsePaint.color = baseColor
                    beaconPulsePaint.alpha = alphaPulse
                    beaconCenterPaint.color = baseColor
                    beaconCenterPaint.alpha = 255

                    // Position X : bord droit extrême
                    val beaconX = plotRight - 6f

                    // 1. Halo pulsant extérieur (LED Aura)
                    native.drawCircle(beaconX, peakY, pulseRadius, beaconPulsePaint)
                    // 2. Centre lumineux solide
                    native.drawCircle(beaconX, peakY, 6f, beaconCenterPaint)
                }
            }

            // --- DESSIN DU CALQUE H1 (Pointillé Cyan Fluo) ---
            
        // --- MANUAL SMART TRACKING OVERLAYS ---
        if (isReportModeActive) {
            fun mapAnchorToScreen(anchor: ManualOrderAnchor): Offset {
                val numFrames = history.size
                val vX = (anchor.frameIndex.toFloat() / (numFrames - 1).coerceAtLeast(1)) * plotWidth
                val vY = ((maxBin - 1 - anchor.exactBinF) / (displayedBinCount - 1).coerceAtLeast(1)) * plotHeight
                
                val screenX = marginLeft + vX * zoom + pan.x
                val screenY = marginTop + vY * zoom + pan.y
                return Offset(screenX, screenY)
            }
            
            // On s'assure que le dessin est clippé à la zone du plot !
            clipRect(
                left = marginLeft, top = marginTop, right = plotRight, bottom = plotBottom
            ) {
                // 1. Draw Validated Orders
                manualTrackedOrders.forEach { order ->
                    val isSelected = order == selectedManualOrder || isBrillanceModeEnabled
                    val path = androidx.compose.ui.graphics.Path()
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
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 14f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10f)
                            )
                        )
                        // Ligne centrale surbrillance
                        drawPath(
                            path = path,
                            color = order.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 6f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10f)
                            )
                        )
                    } else {
                        // Contour noir subtil pour garantir le contraste sur fond clair/rouge/jaune
                        drawPath(
                            path = path,
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 5.5f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10f)
                            )
                        )
                        // Ligne centrale de couleur, épaisseur moyenne, semi-transparente
                        drawPath(
                            path = path,
                            color = order.color.copy(alpha = 0.6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(10f)
                            )
                        )
                    }
                }
    
                // Draw manual tags
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val bgPaint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                    
                    val occupiedRects = mutableListOf<android.graphics.RectF>()
                    
                    manualTrackedOrders.forEach { order ->
                        val isSelected = order == selectedManualOrder || isBrillanceModeEnabled
                        if (isSelected && order.path.isNotEmpty()) {
                            val lastAnchor = order.path.last()
                            val pt = mapAnchorToScreen(lastAnchor)
                            val text = order.name
                            val textWidth = textPaint.measureText(text)
                            val paddingX = 16f
                            val paddingY = 10f
                            
                            val boxWidth = textWidth + paddingX * 2
                            val boxHeight = 35f + paddingY * 2
                            
                            var tx = pt.x + 15f
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
                                val shift = ((offsetStep + 1) / 2) * 20f * direction
                                
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
                            
                            val orderColorInt = android.graphics.Color.argb(
                                (0.8f * 255).toInt(), 
                                (order.color.red * 255).toInt(), 
                                (order.color.green * 255).toInt(), 
                                (order.color.blue * 255).toInt()
                            )
                            bgPaint.color = orderColorInt
                            
                            nativeCanvas.drawRoundRect(currentRect, 12f, 12f, bgPaint)
                            
                            bgPaint.style = android.graphics.Paint.Style.STROKE
                            bgPaint.strokeWidth = 2f
                            bgPaint.color = android.graphics.Color.WHITE
                            nativeCanvas.drawRoundRect(currentRect, 12f, 12f, bgPaint)
                            bgPaint.style = android.graphics.Paint.Style.FILL
                            
                            val distY = Math.abs(currentRect.centerY() - pt.y)
                            if (distY > 20f) {
                                val linePaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    strokeWidth = 2f
                                    isAntiAlias = true
                                    alpha = 150
                                }
                                val boxEdgeX = if (tx > pt.x) currentRect.left else currentRect.right
                                nativeCanvas.drawLine(pt.x, pt.y, boxEdgeX, currentRect.centerY(), linePaint)
                            }
                            
                            nativeCanvas.drawText(text, currentRect.left + paddingX, currentRect.bottom - paddingY - 2f, textPaint)
                        }
                    }
                }
                
                // 2. Draw Current Smart Path
                if (currentSmartPath.isNotEmpty()) {
                    val path = androidx.compose.ui.graphics.Path()
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
                        color = Color.White.copy(alpha = 0.9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
    
                // 3. Draw Current User Anchor Points
                currentUserPoints.forEach { anchor ->
                    val pt = mapAnchorToScreen(anchor)
                    // Halo clignotant
                    drawCircle(
                        color = Color.Red.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = pt
                    )
                    // Bordure extérieure noire pour le contraste
                    drawCircle(
                        color = Color.Black,
                        radius = 7f,
                        center = pt
                    )
                    // Point rouge central (plus grand)
                    drawCircle(
                        color = Color.Red,
                        radius = 6f,
                        center = pt
                    )
                    // Cœur blanc
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = pt
                    )
                }
            }
        }

        if (showH1Overlay && kinematicsConfig.isEnabled && telemetryHistory.isNotEmpty()) {
                val path = android.graphics.Path()
                var isFirst = true
                val numFrames = telemetryHistory.size
                
                for (x in 0 until plotWidth.toInt()) {
                    val exactIdx = if (isWavAnalyzerMode) {
                        (x.toFloat() * (numFrames - 1)) / maxOf(1f, plotWidth - 1f)
                    } else {
                        val reversedX = maxOf(0f, plotWidth - 1f - x.toFloat())
                        (reversedX * (numFrames - 1)) / maxOf(1f, plotWidth - 1f)
                    }
                    val idxBefore = exactIdx.toInt().coerceIn(0, numFrames - 1)
                    val idxAfter = (idxBefore + 1).coerceIn(0, numFrames - 1)
                    val fraction = exactIdx - idxBefore

                    val telemBefore = telemetryHistory[idxBefore]
                    val telemAfter = telemetryHistory[idxAfter]
                    
                    val speedBefore = if (kinematicsConfig.isEnabled && telemBefore.theoreticalSpeedKmh > 0.1f) telemBefore.theoreticalSpeedKmh else telemBefore.speedKmh
                    val speedAfter = if (kinematicsConfig.isEnabled && telemAfter.theoreticalSpeedKmh > 0.1f) telemAfter.theoreticalSpeedKmh else telemAfter.speedKmh
                    
                    val speed = speedBefore + fraction * (speedAfter - speedBefore)
                    
                    if (speed > 1.0f) {
                        val h1Freq = kinematicsConfig.calculateH1FreqHz(speed)
                        val projectedFreq = h1Freq * projectedOrder
                        if (projectedFreq >= actualMinFreq && projectedFreq <= actualMaxFreq) {
                            val freqFraction = (projectedFreq - actualMinFreq) / (actualMaxFreq - actualMinFreq)
                            val y = plotBottom - (freqFraction * plotHeight)
                            val xPos = marginLeft + x
                            
                            if (isFirst) {
                                path.moveTo(xPos, y.toFloat())
                                isFirst = false
                            } else {
                                path.lineTo(xPos, y.toFloat())
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
                val tagBgPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val tagBorderPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                    isAntiAlias = true
                }
                val tagTextPaint = Paint().apply {
                    color = AndroidColor.WHITE
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val nowMs = System.currentTimeMillis()
                val maxHoldMs = (kinematicsConfig.holdTimeSec * 1000.0).toLong().coerceAtLeast(1000L)
                var yOffsetAccumulator = 0f

                for (tag in trackedHarmonicTags) {
                    val ageMs = nowMs - tag.lastSeenTimestampMs
                    val alphaRatio = (1f - (ageMs.toFloat() / maxHoldMs.toFloat())).coerceIn(0f, 1f)
                    if (alphaRatio <= 0f) continue

                    val binFraction = (tag.binIndex - minBin).toFloat() / displayedBinCount.coerceAtLeast(1)
                    if (binFraction !in 0f..1f) continue

                    val basePeakY = marginTop + ((1f - binFraction) * plotHeight).coerceIn(0f, plotHeight)
                    val peakY = (basePeakY + yOffsetAccumulator).coerceIn(marginTop, plotBottom - 30f)

                    val isCritical = tag.ttnrDb >= 6.0
                    val primaryColor = if (isCritical) AndroidColor.parseColor("#FF1744") else AndroidColor.parseColor("#FFC107")
                    
                    val alphaInt = (alphaRatio * 255).toInt().coerceIn(0, 255)
                    tagBgPaint.color = AndroidColor.parseColor("#E6121212")
                    tagBgPaint.alpha = (alphaRatio * 230).toInt()
                    
                    tagBorderPaint.color = primaryColor
                    tagBorderPaint.alpha = alphaInt
                    
                    tagTextPaint.color = primaryColor
                    tagTextPaint.alpha = alphaInt

                    val label = "${tag.orderName} (+%.1fdB)".format(tag.ttnrDb)
                    val textWidth = tagTextPaint.measureText(label)
                    val badgeH = 32f
                    val badgeW = textWidth + 18f

                    // Position X : bord droit décalé
                    val tagX = plotRight - badgeW - 15f
                    val tagYTop = (peakY - badgeH / 2f).coerceIn(marginTop, plotBottom - badgeH)

                    // Dessin du badge d'harmonique
                    native.drawRoundRect(tagX, tagYTop, tagX + badgeW, tagYTop + badgeH, 6f, 6f, tagBgPaint)
                    native.drawRoundRect(tagX, tagYTop, tagX + badgeW, tagYTop + badgeH, 6f, 6f, tagBorderPaint)
                    native.drawText(label, tagX + 9f, tagYTop + 22f, tagTextPaint)
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
                val badgePaddingHorizontal = 12f
                val badgeHeight = 38f

                val badgeLeft = marginLeft + 10f
                val badgeTop = (cursorY - badgeHeight / 2f).coerceIn(marginTop, plotBottom - badgeHeight)
                val badgeRight = badgeLeft + badgeTextWidth + (badgePaddingHorizontal * 2f)
                val badgeBottom = badgeTop + badgeHeight

                native.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 8f, 8f, cursorBadgeBgPaint)
                native.drawText(freqStr, badgeLeft + badgePaddingHorizontal, badgeTop + 28f, cursorBadgeTextPaint)
            }

            // --- AXE X (Temps en secondes) ---
            native.drawLine(marginLeft, plotBottom, plotRight, plotBottom, tickPaint)

            val hopSize = fftSize / 2.0
            val dt = hopSize / sampleRate
            val totalTimeSec = if ((isWavAnalyzerMode || isReportModeActive) && history.isNotEmpty()) (history.size * dt) else (historySize * dt)
            
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
            val xTitle = "Temps (s)"
            val xTitleWidth = textPaint.measureText(xTitle)
            native.drawText(xTitle, marginLeft + (plotWidth - xTitleWidth) / 2f, h - 20f, textPaint)

            // --- LÉGENDE (Affichée désormais de manière fluide en UI Compose sous les boutons) ---
        }
    }
}
