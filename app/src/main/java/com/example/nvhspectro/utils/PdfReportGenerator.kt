package com.example.nvhspectro.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.KinematicsInputMode
import com.example.nvhspectro.data.SmartTrackedOrder
import java.io.OutputStream

object PdfReportGenerator {
    
    // Copied from SpectrogramColormap.kt
    private fun getJetColorInt(v: Float): Int {
        val vClamped = v.coerceIn(0f, 1f)
        var r = 0f
        var g = 0f
        var b = 0f
        if (vClamped < 0.125f) {
            b = 0.5f + 4f * vClamped
        } else if (vClamped < 0.375f) {
            b = 1f
            g = 4f * (vClamped - 0.125f)
        } else if (vClamped < 0.625f) {
            r = 4f * (vClamped - 0.375f)
            g = 1f
            b = 1f - 4f * (vClamped - 0.375f)
        } else if (vClamped < 0.875f) {
            r = 1f
            g = 1f - 4f * (vClamped - 0.625f)
        } else {
            r = 1f - 4f * (vClamped - 0.875f)
        }
        return Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    private fun createBitmapFromHistory(history: List<DoubleArray>, minVal: Double, maxVal: Double, isTtnr: Boolean): Bitmap? {
        if (history.isEmpty()) return null
        
        val width = history.size
        val height = history[0].size
        val pixels = IntArray(width * height)
        
        for (x in 0 until width) {
            val frame = history[x]
            for (y in 0 until height) {
                // Y-axis is inverted (0 is top, maxFreq is top in spectrogram)
                val binIndex = (height - 1) - y
                val magnitude = if (binIndex in frame.indices) frame[binIndex] else minVal
                
                val colorInt = if (isTtnr && magnitude < 1.0) {
                    Color.BLACK
                } else {
                    val rawNormalized = ((magnitude - minVal) / (maxVal - minVal)).coerceIn(0.0, 1.0).toFloat()
                    val normalized = if (isTtnr && rawNormalized > 0f) {
                        Math.pow(rawNormalized.toDouble(), 0.65).toFloat()
                    } else {
                        rawNormalized
                    }
                    getJetColorInt(normalized)
                }
                pixels[y * width + x] = colorInt
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun generateReport(
        context: Context,
        outStream: OutputStream,
        historyAbs: List<DoubleArray>,
        historyTtnr: List<DoubleArray>,
        minDb: Double,
        maxDb: Double,
        trackedOrders: List<SmartTrackedOrder>,
        kinematicsConfig: KinematicsConfig
    ) {
        val absoluteBitmap = createBitmapFromHistory(historyAbs, minDb, maxDb, false)
        val ttnrBitmap = createBitmapFromHistory(historyTtnr, 1.0, 20.0, true) // TTNR usually 1-20

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Drawing properties
        val primaryBlue = Color.parseColor("#0055A4") // Professional deep blue
        val titlePaint = Paint().apply {
            color = primaryBlue
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val appNamePaint = Paint().apply {
            color = primaryBlue
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val textBoldPaint = Paint(textPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.DKGRAY
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = primaryBlue
        }
        
        var currentY = 40f
        val margin = 30f
        val pageWidth = 595f
        
        // --- 1. Header ---
        canvas.drawText("NVHSpectro", margin, currentY + 10f, appNamePaint)
        
        val title = "Rapport des émergences habitacle"
        canvas.drawText(title, pageWidth / 2f, currentY + 12f, titlePaint)
        
        // Placeholder for Logo (Right)
        val logoWidth = 60f
        val logoHeight = 25f
        val logoRect = RectF(pageWidth - margin - logoWidth, currentY - 5f, pageWidth - margin, currentY - 5f + logoHeight)
        canvas.drawRect(logoRect, boxPaint)
        
        val logoTextPaint = Paint(textPaint).apply { textAlign = Paint.Align.CENTER; color = Color.GRAY }
        canvas.drawText("[Logo]", logoRect.centerX(), logoRect.centerY() + 4f, logoTextPaint)
        
        currentY += 35f
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
        currentY += 15f
        
        // --- 2. Study Info Box ---
        val infoBoxTop = currentY
        val infoBoxBottom = infoBoxTop + 65f
        canvas.drawRect(margin, infoBoxTop, pageWidth - margin, infoBoxBottom, boxPaint)
        
        val col1X = margin + 10f
        val col2X = pageWidth / 2f
        
        canvas.drawText("Véhicule : ${kinematicsConfig.vehicleName.ifEmpty { "Non renseigné" }}", col1X, infoBoxTop + 20f, textBoldPaint)
        canvas.drawText("GMPe : ${kinematicsConfig.motorName.ifEmpty { "Non renseigné" }}", col2X, infoBoxTop + 20f, textBoldPaint)
        
        val v1000Str = String.format(java.util.Locale.US, "%.2f km/h", kinematicsConfig.getEffectiveV1000())
        val methodStr = when (kinematicsConfig.inputMode) {
            KinematicsInputMode.DIRECT_V1000 -> "(Saisie directe)"
            KinematicsInputMode.GLOBAL_RATIO -> "(Rapport global)"
            KinematicsInputMode.DETAILED_GEAR -> "(Chaîne cinématique détaillée)"
        }
        val v1000Text = if (kinematicsConfig.isEnabled) "V1000 : $v1000Str $methodStr" else "V1000 : Non activée"
        canvas.drawText(v1000Text, col1X, infoBoxTop + 45f, textBoldPaint)
        
        currentY = infoBoxBottom + 20f
        
        // Helper to draw a colormap
        fun drawColormapBox(canvas: Canvas, bitmap: Bitmap?, x: Float, y: Float, width: Float, height: Float, boxTitle: String, drawBrilliance: Boolean = false) {
            canvas.drawText(boxTitle, x, y - 5f, textBoldPaint)
            val rect = RectF(x, y, x + width, y + height)
            canvas.drawRect(rect, boxPaint)
            
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, rect, Paint())
                
                if (drawBrilliance) {
                    val overlay = Paint().apply { color = Color.parseColor("#99000000") } // 60% black
                    canvas.drawRect(rect, overlay)
                    
                    val pathPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val numFrames = bitmap.width.coerceAtLeast(1)
                    val numBins = bitmap.height.coerceAtLeast(1)
                    
                    trackedOrders.forEachIndexed { index, order ->
                        val hue = (index * 137.5f) % 360f
                        val hsv = floatArrayOf(hue, 1f, 1f)
                        pathPaint.color = Color.HSVToColor(hsv)
                        
                        val androidPath = Path()
                        var isFirst = true
                        order.path.forEach { anchor ->
                            val ptX = x + (anchor.frameIndex.toFloat() / (numFrames - 1)) * width
                            val ptY = y + ((numBins - 1 - anchor.exactBinF) / (numBins - 1)) * height
                            
                            if (isFirst) {
                                androidPath.moveTo(ptX, ptY)
                                isFirst = false
                            } else {
                                androidPath.lineTo(ptX, ptY)
                            }
                        }
                        canvas.drawPath(androidPath, pathPaint)
                    }
                }
            } else {
                val p = Paint(textPaint).apply { color = Color.LTGRAY; textAlign = Paint.Align.CENTER; textSize = 12f }
                canvas.drawText("Aucune donnée", rect.centerX(), rect.centerY(), p)
            }
        }
        
        // --- 3. Row 1: Absolute and TTNR Maps ---
        val mapWidth = 260f
        val mapHeight = 160f
        val leftX = margin
        val rightX = pageWidth - margin - mapWidth
        
        drawColormapBox(canvas, absoluteBitmap, leftX, currentY, mapWidth, mapHeight, "Colormap Absolue (Niveau Global)", false)
        drawColormapBox(canvas, ttnrBitmap, rightX, currentY, mapWidth, mapHeight, "Colormap TTNR (Émergence)", false)
        
        currentY += mapHeight + 30f
        
        // --- 4. Row 2: Tracked Orders List and Brilliance Maps ---
        // Orders List
        canvas.drawText("Ordres Identifiés :", leftX, currentY - 5f, textBoldPaint)
        val listRect = RectF(leftX, currentY, leftX + mapWidth, currentY + mapHeight)
        canvas.drawRect(listRect, boxPaint)
        
        var listY = currentY + 15f
        if (trackedOrders.isEmpty()) {
            canvas.drawText("Aucun ordre tracé.", leftX + 10f, listY, textPaint)
        } else {
            trackedOrders.forEachIndexed { index, order ->
                val hue = (index * 137.5f) % 360f
                val hsv = floatArrayOf(hue, 1f, 1f)
                val dotPaint = Paint().apply { color = Color.HSVToColor(hsv); style = Paint.Style.FILL; isAntiAlias = true }
                
                // Draw color dot
                canvas.drawCircle(leftX + 15f, listY - 4f, 4f, dotPaint)
                
                val eMax = String.format(java.util.Locale.US, "%.1f", order.maxEmergenceDb)
                val fRange = "${order.minFreqHz} - ${order.maxFreqHz} Hz"
                
                canvas.drawText("${order.name} (Emergence Max: $eMax dB)", leftX + 25f, listY, textBoldPaint)
                listY += 12f
                
                if (kinematicsConfig.isEnabled) {
                    val sMin = String.format(java.util.Locale.US, "%.1f", order.minSpeedKmh ?: 0f)
                    val sMax = String.format(java.util.Locale.US, "%.1f", order.maxSpeedKmh ?: 0f)
                    val rMin = order.minRpm ?: 0
                    val rMax = order.maxRpm ?: 0
                    canvas.drawText("Plage: $rMin - $rMax RPM  |  $sMin - $sMax km/h", leftX + 25f, listY, textPaint)
                    listY += 12f
                }
                
                canvas.drawText("Fréquences: $fRange", leftX + 25f, listY, textPaint)
                listY += 18f
            }
        }
        
        // Brilliance Colormaps on the right, stacked vertically, half height
        val smallMapHeight = (mapHeight - 20f) / 2f
        drawColormapBox(canvas, absoluteBitmap, rightX, currentY, mapWidth, smallMapHeight, "Absolue (Brillance)", true)
        drawColormapBox(canvas, ttnrBitmap, rightX, currentY + smallMapHeight + 20f, mapWidth, smallMapHeight, "TTNR (Brillance)", true)
        
        currentY += mapHeight + 30f
        
        // --- 5. Comments Box ---
        canvas.drawText("Commentaires :", leftX, currentY - 5f, textBoldPaint)
        val commentRect = RectF(leftX, currentY, pageWidth - margin, currentY + 120f)
        canvas.drawRect(commentRect, boxPaint)
        
        val comments = kinematicsConfig.comments.ifEmpty { "..." }
        // Simple word wrap logic
        var cy = currentY + 15f
        val maxTextWidth = pageWidth - (margin * 2) - 20f
        val words = comments.split(" ", "\n")
        var line = ""
        
        for (word in words) {
            if ("\n" in word) {
                val split = word.split("\n")
                for (i in split.indices) {
                    val w = split[i]
                    val testLine = if (line.isEmpty()) w else "$line $w"
                    if (textPaint.measureText(testLine) > maxTextWidth) {
                        canvas.drawText(line, leftX + 10f, cy, textPaint)
                        line = w
                        cy += 14f
                    } else {
                        line = testLine
                    }
                    if (i < split.size - 1) {
                        canvas.drawText(line, leftX + 10f, cy, textPaint)
                        line = ""
                        cy += 14f
                    }
                }
            } else {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (textPaint.measureText(testLine) > maxTextWidth) {
                    canvas.drawText(line, leftX + 10f, cy, textPaint)
                    line = word
                    cy += 14f
                } else {
                    line = testLine
                }
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, leftX + 10f, cy, textPaint)
        }
        
        // Finish Page and write
        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outStream)
        pdfDocument.close()
    }
}
