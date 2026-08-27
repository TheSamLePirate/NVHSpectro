package com.example.nvhspectro.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.nvhspectro.TelemetryData
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhSpectrum
import com.example.nvhspectro.theme.NvhStatusGood
import com.example.nvhspectro.theme.NvhStatusWarn
import com.example.nvhspectro.theme.nvhEmergenceColor
import kotlin.math.max
import kotlin.math.min

enum class TelemetryMetric(
    val label: String,
    val unit: String,
) {
    SPEED("Vitesse", "km/h"),
    ACCELERATION("Accélération", "g"),
    ORDER("Ordre", "dBFS"),
    TTNR("TTNR", "dB"),
}

@Composable
fun TelemetryGraph(
    history: List<TelemetryData>,
    metric: TelemetryMetric,
    timeWindowSec: Double,
    historySize: Int = 150,
    ttnrSpectrum: FloatArray = FloatArray(0),
    minFreq: Int = 0,
    maxFreq: Int = 10000,
    sampleRate: Int,
    isKinematicsEnabled: Boolean = false,
    selectedOrderName: String = "H18",
    isWavAnalyzerMode: Boolean = false,
    wavPlaybackProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val textPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    val warningTextPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.parseColor("#FF9100")
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    val tickTextPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 20f
                isAntiAlias = true
            }
        }

    val axisPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 2.5f
                isAntiAlias = true
            }
        }

    val fineGridPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.parseColor("#334155")
                strokeWidth = 1.5f
                isAntiAlias = true
            }
        }

    val badgeBgPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.parseColor("#CC121824")
                isAntiAlias = true
            }
        }

    val badgeBorderPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }
        }

    val badgeTextPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
        }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val marginLeft = 190f
        val marginRight = 30f
        val marginTop = 20f
        val marginBottom = 45f

        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom

        if (metric == TelemetryMetric.TTNR) {
            // =========================================================================
            // MODE SPECTRE 2D TTNR : GRILLE MULTI-REPÈRES HAUTE PRÉCISION ET LISIBILITÉ
            // ABSCISSE = FRÉQUENCE (Hz), ORDONNÉE = ÉMERGENCE (dB)
            // =========================================================================
            val maxTtnrDb = 20.0
            val totalBins = if (ttnrSpectrum.isNotEmpty()) ttnrSpectrum.size else 1024
            val nyquist = sampleRate / 2
            val minBin = ((minFreq * totalBins) / nyquist).coerceIn(0, totalBins - 1)
            val maxBin = ((maxFreq * totalBins) / nyquist).coerceIn(minBin + 1, totalBins)
            val displayedBins = (maxBin - minBin).coerceAtLeast(1)

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                // 1. Grille Horizontale d'Émergence (0 dB, 5 dB, 10 dB, 15 dB, 20 dB)
                val dbSteps = 4
                for (step in 0..dbSteps) {
                    val dbVal = step * (maxTtnrDb / dbSteps)
                    val y = (marginTop + plotHeight) - (step.toFloat() / dbSteps) * plotHeight
                    native.drawLine(marginLeft, y, marginLeft + plotWidth, y, fineGridPaint)
                    val label =
                        if (step == dbSteps) {
                            "+20 dB"
                        } else if (step == 0) {
                            "0 dB"
                        } else {
                            "+${dbVal.toInt()} dB"
                        }
                    native.drawText(label, 15f, y + 8f, tickTextPaint)
                }

                // 2. Grille Verticale de Fréquence
                val freqSteps = 4
                for (step in 0..freqSteps) {
                    val fraction = step.toFloat() / freqSteps
                    val x = marginLeft + fraction * plotWidth
                    val freqVal = minFreq + (fraction * (maxFreq - minFreq)).toInt()
                    native.drawLine(x, marginTop, x, marginTop + plotHeight, fineGridPaint)
                    val label = "$freqVal Hz"
                    val labelW = tickTextPaint.measureText(label)
                    var labelX = x - (labelW / 2f)
                    labelX = labelX.coerceIn(marginLeft, marginLeft + plotWidth - labelW)
                    native.drawText(label, labelX, marginTop + plotHeight + 35f, tickTextPaint)
                }

                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)
            }

            if (ttnrSpectrum.isNotEmpty() && displayedBins > 1) {
                val path = Path()
                var maxEmergence = 0.0
                var maxEmergenceBin = -1

                for (i in 0 until displayedBins) {
                    val bin = minBin + i
                    val valTtnr = if (bin in ttnrSpectrum.indices) ttnrSpectrum[bin].toDouble() else 0.0
                    if (valTtnr > maxEmergence) {
                        maxEmergence = valTtnr
                        maxEmergenceBin = bin
                    }

                    val fractionX = i.toFloat() / max(1, displayedBins - 1)
                    val x = marginLeft + fractionX * plotWidth

                    val ttnrVal = valTtnr.coerceIn(0.0, maxTtnrDb)
                    val normY = (ttnrVal / maxTtnrDb).toFloat()
                    val y = (marginTop + plotHeight) - (normY * plotHeight)

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                val fillPath =
                    Path().apply {
                        addPath(path)
                        lineTo(marginLeft + plotWidth, marginTop + plotHeight)
                        lineTo(marginLeft, marginTop + plotHeight)
                        close()
                    }

                drawPath(
                    path = fillPath,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(NvhSpectrum.copy(alpha = 0.33f), NvhSpectrum.copy(alpha = 0f)),
                            startY = marginTop,
                            endY = marginTop + plotHeight,
                        ),
                )

                drawPath(
                    path = path,
                    color = NvhSpectrum,
                    style = Stroke(width = 3.5f),
                )

                if (maxEmergence >= 3.0 && maxEmergenceBin >= minBin) {
                    val peakFreq = ((maxEmergenceBin.toDouble() / totalBins) * nyquist).toInt()
                    val peakFractionX = (maxEmergenceBin - minBin).toFloat() / max(1, displayedBins - 1)
                    val peakX = marginLeft + peakFractionX * plotWidth
                    val peakNormY = (maxEmergence.coerceIn(0.0, maxTtnrDb) / maxTtnrDb).toFloat()
                    val peakY = (marginTop + plotHeight) - (peakNormY * plotHeight)

                    drawLine(
                        color = NvhAccent,
                        start = Offset(peakX, marginTop),
                        end = Offset(peakX, marginTop + plotHeight),
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                    )

                    drawCircle(
                        color = NvhAccent,
                        radius = 6f,
                        center = Offset(peakX, peakY),
                    )

                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val badgeText = "$peakFreq Hz | +${String.format("%.1f", maxEmergence)} dB"
                        val textWidth = badgeTextPaint.measureText(badgeText)

                        var badgeLeft = peakX - (textWidth / 2f) - 16f
                        badgeLeft = badgeLeft.coerceIn(marginLeft, marginLeft + plotWidth - textWidth - 32f)

                        val badgeTop = marginTop + 10f
                        val badgeRight = badgeLeft + textWidth + 32f
                        val badgeBottom = badgeTop + 36f

                        val rect = android.graphics.RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
                        native.drawRoundRect(rect, 12f, 12f, badgeBgPaint)
                        native.drawRoundRect(rect, 12f, 12f, badgeBorderPaint)
                        native.drawText(badgeText, badgeLeft + 16f, badgeTop + 26f, badgeTextPaint)
                    }
                }
            }
        } else if (metric == TelemetryMetric.ORDER &&
            (!isKinematicsEnabled || (history.isNotEmpty() && history.none { it.theoreticalSpeedKmh > 1.0f || it.speedKmh > 1.0f }))
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)

                val msg =
                    if (!isKinematicsEnabled) {
                        "⚠️ Disponible uniquement en mode étude GMPe activé"
                    } else {
                        "⚠️ Suivi d'Ordre inactif (< 1 km/h)"
                    }
                val textW = warningTextPaint.measureText(msg)
                val msgX = marginLeft + (plotWidth - textW) / 2f
                val msgY = marginTop + plotHeight / 2f
                native.drawText(msg, msgX, msgY, warningTextPaint)
            }
        } else {
            val values =
                history.map { data ->
                    when (metric) {
                        TelemetryMetric.SPEED -> {
                            if (isKinematicsEnabled && data.theoreticalSpeedKmh > 0f) {
                                data.theoreticalSpeedKmh.toDouble()
                            } else {
                                data.speedKmh.toDouble()
                            }
                        }
                        TelemetryMetric.ACCELERATION -> data.accelerationG.toDouble()
                        TelemetryMetric.ORDER -> data.trackedOrderDbFS.coerceIn(-120.0, 0.0)
                        else -> 0.0
                    }
                }

            val minVal =
                if (metric == TelemetryMetric.ORDER) {
                    val currentMin = if (values.isNotEmpty()) values.minOrNull() ?: -110.0 else -110.0
                    max(-110.0, currentMin - 20.0)
                } else {
                    if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
                }
            val maxVal =
                if (metric == TelemetryMetric.ORDER) {
                    val currentMax = if (values.isNotEmpty()) values.maxOrNull() ?: 0.0 else 0.0
                    min(0.0, currentMax + 20.0)
                } else {
                    if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0
                }
            val valRange = if (maxVal > minVal) maxVal - minVal else 1.0

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)

                val maxStr = String.format("%.1f %s", maxVal, metric.unit)
                val minStr = String.format("%.1f %s", minVal, metric.unit)
                native.drawText(maxStr, 12f, marginTop + 22f, textPaint)
                native.drawText(minStr, 12f, marginTop + plotHeight - 4f, textPaint)
            }

            if (values.size > 1) {
                val pointCount = values.size
                val targetHistSize = if (isWavAnalyzerMode) pointCount else max(historySize, pointCount)

                if (metric == TelemetryMetric.ORDER) {
                    for (i in 0 until pointCount - 1) {
                        // [plan 3.4] History is chronological (newest LAST); the live
                        // view anchors the newest sample to the right edge.
                        val fractionX1 =
                            if (isWavAnalyzerMode) {
                                i.toFloat() / max(1, pointCount - 1)
                            } else {
                                (pointCount - 1 - i).toFloat() /
                                    max(1, targetHistSize - 1)
                            }
                        val x1 = if (isWavAnalyzerMode) marginLeft + fractionX1 * plotWidth else marginLeft + (1f - fractionX1) * plotWidth
                        val normY1 = ((values[i] - minVal) / valRange).toFloat()
                        val y1 = (marginTop + plotHeight) - (normY1 * plotHeight)

                        val fractionX2 =
                            if (isWavAnalyzerMode) {
                                (i + 1).toFloat() / max(1, pointCount - 1)
                            } else {
                                (pointCount - 2 - i).toFloat() /
                                    max(1, targetHistSize - 1)
                            }
                        val x2 = if (isWavAnalyzerMode) marginLeft + fractionX2 * plotWidth else marginLeft + (1f - fractionX2) * plotWidth
                        val normY2 = ((values[i + 1] - minVal) / valRange).toFloat()
                        val y2 = (marginTop + plotHeight) - (normY2 * plotHeight)

                        val segColor = nvhEmergenceColor(history[i].trackedOrderEmergenceDb)

                        drawLine(
                            color = segColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 4f,
                        )
                    }
                } else {
                    val path = Path()
                    for (i in 0 until pointCount) {
                        // [plan 3.4] Chronological history; live anchors newest right.
                        val fractionX =
                            if (isWavAnalyzerMode) {
                                i.toFloat() / max(1, pointCount - 1)
                            } else {
                                (pointCount - 1 - i).toFloat() /
                                    max(1, targetHistSize - 1)
                            }
                        val x = if (isWavAnalyzerMode) marginLeft + fractionX * plotWidth else marginLeft + (1f - fractionX) * plotWidth

                        val normY = ((values[i] - minVal) / valRange).toFloat()
                        val y = (marginTop + plotHeight) - (normY * plotHeight)

                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    val strokeColor =
                        when (metric) {
                            TelemetryMetric.SPEED -> NvhStatusGood
                            TelemetryMetric.ACCELERATION -> NvhStatusWarn
                            else -> NvhOnSurface
                        }

                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = 4f),
                    )
                }
            }

            // Curseur de lecture vertical en mode Analyseur WAV
            if (isWavAnalyzerMode) {
                val xCursor = marginLeft + (wavPlaybackProgress.coerceIn(0f, 1f) * plotWidth)
                drawLine(
                    color = NvhModeWavAccent,
                    start = Offset(xCursor, marginTop),
                    end = Offset(xCursor, marginTop + plotHeight),
                    strokeWidth = 4f,
                )
                drawCircle(
                    color = NvhModeWavAccent,
                    radius = 7f,
                    center = Offset(xCursor, marginTop),
                )
            }
        }
    }
}
