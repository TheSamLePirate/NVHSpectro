package com.example.nvhspectro.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.nvhspectro.AudioConfig
import com.example.nvhspectro.R
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.KinematicsInputMode
import com.example.nvhspectro.data.SmartTrackedOrder
import java.io.OutputStream
import kotlin.math.ceil

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

    private fun createBitmapFromHistory(history: List<FloatArray>, minVal: Double, maxVal: Double, isTtnr: Boolean, maxBin: Int, maskBelowBin: Int = 0): Bitmap? {
        if (history.isEmpty()) return null
        
        val width = history.size
        val height = maxBin.coerceAtMost(history[0].size)
        if (height <= 0) return null
        val pixels = IntArray(width * height)
        
        for (x in 0 until width) {
            val frame = history[x]
            for (y in 0 until height) {
                // Y-axis is inverted (0 is top, maxFreq is top in spectrogram)
                val binIndex = (height - 1) - y
                // [D7] Display-layer sub-30 Hz floor.
                val magnitude = if (binIndex < maskBelowBin || binIndex !in frame.indices) minVal else frame[binIndex].toDouble()
                
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
        historyAbs: List<FloatArray>,
        historyTtnr: List<FloatArray>,
        minDb: Double,
        maxDb: Double,
        trackedOrders: List<SmartTrackedOrder>,
        kinematicsConfig: KinematicsConfig,
        globalMaxFreq: Float,
        sampleRate: Int
    ) {
        val nyquist = sampleRate / 2
        // hop size == fftSize/2 == bin count, so duration derives from the data itself.
        val totalBinCount = historyAbs.firstOrNull()?.size ?: (AudioConfig.WAV_FFT_SIZE / 2)

        // Calcul de la freq max dynamique
        val maxTrackedFreq = trackedOrders.maxOfOrNull { it.maxFreqHz.toFloat() } ?: 0f
        var pdfMaxFreq = if (maxTrackedFreq > 0) {
            val target = maxTrackedFreq + 500f
            ceil(target / 250f).toFloat() * 250f
        } else {
            globalMaxFreq
        }
        if (pdfMaxFreq > nyquist) pdfMaxFreq = nyquist.toFloat()

        val maxBin = ((pdfMaxFreq * totalBinCount) / nyquist).toInt().coerceIn(1, totalBinCount)

        // [D7] Display-layer sub-30 Hz floor (data stays true).
        val maskBelowBin = Math.ceil(AudioConfig.DISPLAY_MIN_FREQ_HZ * totalBinCount / nyquist).toInt()
        val absoluteBitmap = createBitmapFromHistory(historyAbs, minDb, maxDb, false, maxBin, maskBelowBin)
        val ttnrBitmap = createBitmapFromHistory(historyTtnr, 1.0, 20.0, true, maxBin, maskBelowBin)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Drawing properties
        val primaryBlue = Color.parseColor("#0055A4") // Professional deep blue
        val titlePaint = Paint().apply {
            color = primaryBlue
            textSize = 18f
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
        val axisPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 7f
            isAntiAlias = true
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
        
        // Logo
        val logoBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.vibratec_logo)
        } catch (e: Exception) {
            null
        }
        if (logoBitmap != null) {
            val aspectRatio = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
            val logoHeight = 30f
            val logoWidth = logoHeight * aspectRatio
            val logoRect = RectF(pageWidth - margin - logoWidth, currentY - 15f, pageWidth - margin, currentY - 15f + logoHeight)
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Title multi-line
        canvas.drawText("Rapport des émergences", pageWidth / 2f, currentY, titlePaint)
        canvas.drawText("habitacle", pageWidth / 2f, currentY + 22f, titlePaint)
        
        currentY += 45f
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
        currentY += 15f
        
        // --- 2. Study Info Box ---
        val textBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        
        val infoBoxTop = currentY
        val infoBoxBottom = infoBoxTop + 65f
        canvas.drawRect(margin, infoBoxTop, pageWidth - margin, infoBoxBottom, boxPaint)
        
        val col1X = margin + 10f
        val col2X = pageWidth / 2f
        
        canvas.drawText("Véhicule : ${kinematicsConfig.vehicleName.ifEmpty { "Non renseigné" }}", col1X, infoBoxTop + 20f, textBoldPaint)
        canvas.drawText("GMPe : ${kinematicsConfig.motorName.ifEmpty { "Non renseigné" }}", col2X, infoBoxTop + 20f, textBoldPaint)
        
        val v1000Str = String.format(java.util.Locale.US, "%.2f km/h", kinematicsConfig.getEffectiveV1000())
        val methodStr = when (kinematicsConfig.inputMode) {
            KinematicsInputMode.V1000 -> "(Saisie directe)"
            KinematicsInputMode.GEAR_RATIO -> "(Rapport global)"
            KinematicsInputMode.DETAILED_CHAIN -> "(Chaîne cinématique détaillée)"
        }
        val v1000Text = if (kinematicsConfig.isEnabled) "V1000 : $v1000Str $methodStr" else "V1000 : Non activée"
        canvas.drawText(v1000Text, col1X, infoBoxTop + 45f, textBoldPaint)
        
        currentY = infoBoxBottom + 25f
        
        val totalDurationSeconds = (historyAbs.size.toFloat() * totalBinCount) / sampleRate
        
        // Helper to draw a colormap
        fun drawColormapBox(canvas: Canvas, bitmap: Bitmap?, x: Float, y: Float, width: Float, height: Float, boxTitle: String, drawBrilliance: Boolean = false) {
            canvas.drawText(boxTitle, x, y - 5f, textBoldPaint)
            
            val contentX = x + 35f // + de place pour les axes Y
            val contentY = y
            val contentWidth = width - 35f - 10f // marge a droite
            val contentHeight = height - 15f
            val rect = RectF(contentX, contentY, contentX + contentWidth, contentY + contentHeight)
            
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
                    
                    trackedOrders.forEachIndexed { index, order ->
                        val hue = (index * 137.5f) % 360f
                        val hsv = floatArrayOf(hue, 1f, 1f)
                        pathPaint.color = Color.HSVToColor(hsv)
                        
                        val androidPath = Path()
                        var isFirst = true
                        order.path.forEach { anchor ->
                            val ptX = contentX + (anchor.frameIndex.toFloat() / (numFrames - 1)) * contentWidth
                            val binFInNewScale = anchor.exactBinF
                            val ptY = contentY + ((maxBin - 1 - binFInNewScale) / (maxBin - 1)) * contentHeight
                            
                            if (binFInNewScale < maxBin) {
                                if (isFirst) {
                                    androidPath.moveTo(ptX, ptY)
                                    isFirst = false
                                } else {
                                    androidPath.lineTo(ptX, ptY)
                                }
                            }
                        }
                        canvas.drawPath(androidPath, pathPaint)
                    }
                }
            } else {
                val p = Paint(textPaint).apply { color = Color.LTGRAY; textAlign = Paint.Align.CENTER; textSize = 12f }
                canvas.drawText("Aucune donnée", rect.centerX(), rect.centerY(), p)
            }
            
            // Draw Axes
            val pY = Paint(axisPaint).apply { textAlign = Paint.Align.RIGHT }
            val ySteps = 5 // 6 points
            for (i in 0..ySteps) {
                val fraction = i.toFloat() / ySteps
                val freq = (fraction * pdfMaxFreq).toInt()
                val yy = contentY + contentHeight - (fraction * contentHeight)
                canvas.drawText("${freq}Hz", contentX - 4f, yy + 3f, pY)
                // draw tick mark
                canvas.drawLine(contentX - 2f, yy, contentX, yy, pY)
            }
            canvas.save()
            canvas.translate(x + 5f, contentY + contentHeight / 2f)
            canvas.rotate(-90f)
            canvas.drawText("Fréq (Hz)", 0f, 0f, Paint(axisPaint).apply { textAlign = Paint.Align.CENTER })
            canvas.restore()
            
            val pX = Paint(axisPaint).apply { textAlign = Paint.Align.CENTER }
            val xSteps = 6 // 7 points
            for (i in 0..xSteps) {
                val fraction = i.toFloat() / xSteps
                val timeS = fraction * totalDurationSeconds
                val xx = contentX + (fraction * contentWidth)
                val timeStr = String.format(java.util.Locale.US, "%.1fs", timeS)
                canvas.drawText(timeStr, xx, contentY + contentHeight + 10f, pX)
                // draw tick mark
                canvas.drawLine(xx, contentY + contentHeight, xx, contentY + contentHeight + 2f, pX)
            }
        }
        
        // --- 3. Row 1: Absolute and TTNR Maps ---
        val mapWidth = 267f // slightly larger to fill space
        val mapHeight = 150f
        val leftX = margin
        val rightX = pageWidth - margin - mapWidth
        
        drawColormapBox(canvas, absoluteBitmap, leftX, currentY, mapWidth, mapHeight, "Colormap Absolue", false)
        drawColormapBox(canvas, ttnrBitmap, rightX, currentY, mapWidth, mapHeight, "Colormap TTNR", false)
        
        currentY += mapHeight + 25f
        
        // --- 4. Row 2: Tracked Orders List and Brilliance Maps ---
        canvas.drawText("Ordres Identifiés :", leftX, currentY - 5f, textBoldPaint)
        
        val itemsCount = trackedOrders.size
        
        // We must ensure that the list + comments don't overflow 842.
        // Let's reserve 80f for Comments minimum height.
        val maxAvailableHeightForListAndMaps = 842f - currentY - margin - 80f - 20f
        // the Brilliance maps will take 150f + space = 150f.
        
        // Calculate required list height with normal font
        val listWidth = 230f // reduced width for the list
        val normalItemHeight = if (kinematicsConfig.isEnabled) 42f else 28f
        val requiredListHeight = 15f + (itemsCount * normalItemHeight)
        
        // Shrink list scale if it exceeds max available height
        val scale = if (requiredListHeight > maxAvailableHeightForListAndMaps) {
            maxAvailableHeightForListAndMaps / requiredListHeight
        } else {
            1f
        }
        
        val listActualHeight = requiredListHeight * scale
        val listRect = RectF(leftX, currentY, leftX + listWidth, currentY + listActualHeight.coerceAtLeast(mapHeight))
        canvas.drawRect(listRect, boxPaint)
        
        val listTextPaint = Paint(textPaint).apply { textSize = 10f * scale }
        val listBoldPaint = Paint(textBoldPaint).apply { textSize = 10f * scale }
        
        var listY = currentY + 15f * scale
        if (trackedOrders.isEmpty()) {
            canvas.drawText("Aucun ordre tracé.", leftX + 10f, listY, listTextPaint)
        } else {
            trackedOrders.forEachIndexed { index, order ->
                val hue = (index * 137.5f) % 360f
                val hsv = floatArrayOf(hue, 1f, 1f)
                val dotPaint = Paint().apply { color = Color.HSVToColor(hsv); style = Paint.Style.FILL; isAntiAlias = true }
                
                val r = 4f * scale
                canvas.drawCircle(leftX + 10f + r, listY - 4f * scale, r, dotPaint)
                
                val eMax = String.format(java.util.Locale.US, "%.1f", order.maxEmergenceDb)
                val fRange = "${order.minFreqHz} - ${order.maxFreqHz} Hz"
                
                val txtX = leftX + 15f + r*2f
                
                canvas.drawText("${order.name} (Max TTNR: $eMax dB)", txtX, listY, listBoldPaint)
                listY += 12f * scale
                
                if (kinematicsConfig.isEnabled) {
                    val sMin = String.format(java.util.Locale.US, "%.1f", order.minSpeedKmh ?: 0f)
                    val sMax = String.format(java.util.Locale.US, "%.1f", order.maxSpeedKmh ?: 0f)
                    val rMin = order.minRpm ?: 0
                    val rMax = order.maxRpm ?: 0
                    canvas.drawText("Vitesse : $sMin - $sMax km/h", txtX, listY, listTextPaint)
                    listY += 10f * scale
                    canvas.drawText("Régime  : $rMin - $rMax RPM", txtX, listY, listTextPaint)
                    listY += 10f * scale
                }
                
                canvas.drawText("Fréquences: $fRange", txtX, listY, listTextPaint)
                listY += 10f * scale
            }
        }
        
        // Brilliance Colormaps on the right, stacked vertically
        val brillianceMapWidth = pageWidth - margin - (leftX + listWidth + 15f)
        val brillianceLeftX = leftX + listWidth + 15f
        val smallMapHeight = (mapHeight - 20f) / 2f
        drawColormapBox(canvas, absoluteBitmap, brillianceLeftX, currentY, brillianceMapWidth, smallMapHeight, "Absolue (Brillance)", true)
        drawColormapBox(canvas, ttnrBitmap, brillianceLeftX, currentY + smallMapHeight + 20f, brillianceMapWidth, smallMapHeight, "TTNR (Brillance)", true)
        
        // Update currentY based on whichever is taller (the list or the maps)
        currentY += maxOf(listRect.height(), mapHeight) + 20f
        
        // --- 5. Comments Box ---
        canvas.drawText("Commentaires :", leftX, currentY - 5f, textBoldPaint)
        val maxCommentsHeight = 842f - margin - currentY
        val commentsBoxHeight = maxCommentsHeight.coerceAtMost(100f).coerceAtLeast(30f)
        val commentRect = RectF(leftX, currentY, pageWidth - margin, currentY + commentsBoxHeight)
        canvas.drawRect(commentRect, boxPaint)
        
        val comments = kinematicsConfig.comments.ifEmpty { "..." }
        var cy = currentY + 12f
        val maxTextWidth = pageWidth - (margin * 2) - 10f
        val words = comments.split(" ", "\n")
        var line = ""
        
        for (word in words) {
            // Stop drawing if we reach bottom of comment box
            if (cy > currentY + commentsBoxHeight - 10f) break
            
            if ("\n" in word) {
                val split = word.split("\n")
                for (i in split.indices) {
                    if (cy > currentY + commentsBoxHeight - 10f) break
                    val w = split[i]
                    val testLine = if (line.isEmpty()) w else "$line $w"
                    if (textPaint.measureText(testLine) > maxTextWidth) {
                        canvas.drawText(line, leftX + 5f, cy, textPaint)
                        line = w
                        cy += 12f
                    } else {
                        line = testLine
                    }
                    if (i < split.size - 1) {
                        canvas.drawText(line, leftX + 5f, cy, textPaint)
                        line = ""
                        cy += 12f
                    }
                }
            } else {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (textPaint.measureText(testLine) > maxTextWidth) {
                    canvas.drawText(line, leftX + 5f, cy, textPaint)
                    line = word
                    cy += 12f
                } else {
                    line = testLine
                }
            }
        }
        if (line.isNotEmpty() && cy <= currentY + commentsBoxHeight - 5f) {
            canvas.drawText(line, leftX + 5f, cy, textPaint)
        }
        
        // Finish Page and write
        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outStream)
        pdfDocument.close()
    }
}
