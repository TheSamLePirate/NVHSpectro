package com.example.nvhspectro.export

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.R
import com.example.nvhspectro.TelemetryData
import com.example.nvhspectro.getJetColorInt
import kotlin.math.max
import kotlin.math.min

/**
 * [plan 3.3, C6-export] PNG snapshot export of the frozen view — bitmap
 * rendering on a background thread, MediaStore write on IO (the historical
 * exportData built a 1400×1850 canvas plus a per-pixel spectrogram loop on
 * the MAIN thread).
 */
object PngExporter {

    class Input(
        val history: List<DoubleArray>,
        val telemetryHistory: List<TelemetryData>,
        val currentTelemetry: TelemetryData,
        val displayMode: DisplayMode,
        val minDb: Double,
        val maxDb: Double,
        val maxFreq: Int,
        val sampleRate: Int,
        val timeWindowSec: Double,
        val historySize: Int,
        val pedalPercent: String,
        val comments: String
    )

    /** Renders and saves the export PNG. Call from a background dispatcher. */
    fun export(application: Application, input: Input) {
        if (input.history.isEmpty()) return
        val outBitmap = render(application, input)

        val resolver = application.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NVHSpectro_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NVHSpectro")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { outStream ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
        }
    }

    private fun render(application: Application, input: Input): Bitmap {
        val history = input.history
        val bitmapWidth = history.size
        val binCount = history.first().size
        val nyquistFreq = input.sampleRate / 2
        val displayedBinCount = min(binCount, (input.maxFreq * binCount) / nyquistFreq)
        val bitmapHeight = displayedBinCount

        // 1. Raw spectrogram bitmap (absolute or TTNR, matching the display).
        val spectroBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmapWidth * bitmapHeight) { android.graphics.Color.BLACK }
        val mode = input.displayMode
        val minVal = if (mode == DisplayMode.TTNR) 1.0 else input.minDb
        val maxVal = if (mode == DisplayMode.TTNR) 20.0 else input.maxDb

        for (x in 0 until bitmapWidth) {
            val frameData = history[x]
            for (y in 0 until bitmapHeight) {
                val b = bitmapHeight - 1 - y
                val valMagnitude = if (b < frameData.size) frameData[b] else minVal
                val normalized = ((valMagnitude - minVal) / (maxVal - minVal)).toFloat()
                pixels[y * bitmapWidth + (bitmapWidth - 1 - x)] = getJetColorInt(normalized)
            }
        }
        spectroBitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)

        // 2. Output canvas.
        val outWidth = 1400
        val outHeight = 1850
        val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#121212"))

        val paintTitle = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintText = Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 28f
            isAntiAlias = true
        }
        val paintAxis = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintLine = Paint().apply {
            color = android.graphics.Color.GRAY
            strokeWidth = 2f
            isAntiAlias = true
        }

        // --- Header ---
        var curY = 60f
        canvas.drawText("NVH SPECTRO - RAPPORT (${mode.label.uppercase()})", 60f, curY, paintTitle)

        try {
            val logoBitmap = BitmapFactory.decodeResource(application.resources, R.drawable.logo_vibratec)
            if (logoBitmap != null) {
                val logoW = 280f
                val logoH = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * logoW
                val logoRect = RectF(outWidth - logoW - 60f, 30f, outWidth - 60f, 30f + logoH)
                canvas.drawBitmap(logoBitmap, null, logoRect, null)
            }
        } catch (e: Exception) {
            // Logo is decorative; a decode failure must not block the export.
        }

        curY += 45f

        val telemetry = input.currentTelemetry
        val metadataStr = "Vitesse: ${String.format("%.1f", telemetry.speedKmh)} km/h | " +
            "Pédale: ${if (input.pedalPercent.isBlank()) "-" else input.pedalPercent}% | " +
            "Accél: ${String.format("%.2f", telemetry.accelerationG)}g | Mode: ${mode.label}"
        canvas.drawText(metadataStr, 60f, curY, paintText)
        curY += 40f

        if (input.comments.isNotBlank()) {
            canvas.drawText("Commentaires: ${input.comments}", 60f, curY, paintText)
            curY += 40f
        }

        curY += 20f

        val marginLeft = 200f
        val marginRight = 60f
        val plotWidth = outWidth - marginLeft - marginRight

        // --- 1. Spectrogram (500 px) ---
        val spectroHeight = 500f
        val dstRect = RectF(marginLeft, curY, marginLeft + plotWidth, curY + spectroHeight)
        canvas.drawBitmap(spectroBitmap, null, dstRect, null)

        val actualMaxFreq = (displayedBinCount * nyquistFreq) / binCount
        canvas.drawLine(marginLeft, curY, marginLeft, curY + spectroHeight, paintLine)

        val yTicks = 7
        for (i in 0 until yTicks) {
            val fraction = i.toFloat() / (yTicks - 1)
            val yPos = curY + spectroHeight - (fraction * spectroHeight)
            val freqValue = (fraction * actualMaxFreq).toInt()
            canvas.drawLine(marginLeft - 10f, yPos, marginLeft, yPos, paintLine)
            val textYPos = when (i) {
                0 -> yPos
                yTicks - 1 -> yPos + 30f
                else -> yPos + 10f
            }
            canvas.drawText("$freqValue Hz", 20f, textYPos, paintAxis)
        }

        curY += spectroHeight + 60f

        // --- 2. Three stacked telemetry curves ---
        val graphHeight = 220f
        val graphGap = 60f

        fun drawStackedGraph(title: String, unit: String, colorInt: Int, values: List<Double>) {
            val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#1E1E1E") }
            canvas.drawRect(marginLeft, curY, marginLeft + plotWidth, curY + graphHeight, bgPaint)
            canvas.drawLine(marginLeft, curY, marginLeft, curY + graphHeight, paintLine)
            canvas.drawLine(marginLeft, curY + graphHeight, marginLeft + plotWidth, curY + graphHeight, paintLine)

            val minV = if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
            val maxV = if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0
            val rangeV = if (maxV > minV) maxV - minV else 1.0

            canvas.drawText(String.format("%.1f %s", maxV, unit), 20f, curY + 30f, paintAxis)
            canvas.drawText(String.format("%.1f %s", minV, unit), 20f, curY + graphHeight, paintAxis)

            val titlePaint = Paint().apply {
                color = colorInt
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            canvas.drawText(title, marginLeft + 20f, curY + 35f, titlePaint)

            if (values.size > 1) {
                val path = Path()
                val pCount = values.size
                val linePaint = Paint().apply {
                    color = colorInt
                    strokeWidth = 3.5f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                for (i in 0 until pCount) {
                    val fractionX = (pCount - 1 - i).toFloat() / max(1, input.historySize - 1)
                    val x = marginLeft + (1f - fractionX) * plotWidth
                    val normY = ((values[i] - minV) / rangeV).toFloat()
                    val y = (curY + graphHeight) - (normY * graphHeight)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, linePaint)
            }
            curY += graphHeight + graphGap
        }

        drawStackedGraph("Vitesse (km/h)", "km/h", android.graphics.Color.parseColor("#00E676"), input.telemetryHistory.map { it.speedKmh.toDouble() })
        drawStackedGraph("Accélération (g)", "g", android.graphics.Color.parseColor("#FF9100"), input.telemetryHistory.map { it.accelerationG.toDouble() })
        drawStackedGraph("Altitude (m)", "m", android.graphics.Color.parseColor("#00B0FF"), input.telemetryHistory.map { it.altitude })

        val xBottomY = curY - graphGap + 35f
        val xSteps = 5
        for (i in 0..xSteps) {
            val fraction = i.toFloat() / xSteps
            val x = marginLeft + fraction * plotWidth
            val tSec = -input.timeWindowSec * (1f - fraction)
            canvas.drawText(String.format("%.1fs", tSec), x - 25f, xBottomY, paintAxis)
        }
        canvas.drawText("Temps (s)", marginLeft + plotWidth / 2f - 40f, xBottomY + 35f, paintAxis)

        return outBitmap
    }
}
