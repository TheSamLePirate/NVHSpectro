import re

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

PDF_METHOD = '''
    fun generatePdfReport(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                
                val paintText = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                val paintTitle = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                
                // 1. Header
                val kin = _kinematicsConfig.value
                val title = "Rapport d'Analyse Vibratoire (NVH Spectro)"
                canvas.drawText(title, 50f, 50f, paintTitle)
                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                canvas.drawText("Date: \", 50f, 75f, paintText)
                if (kin.isEnabled) {
                    canvas.drawText("Véhicule: \", 50f, 95f, paintText)
                    canvas.drawText("Moteur: \", 50f, 110f, paintText)
                    canvas.drawText("V1000 équivalente: \ km/h", 50f, 125f, paintText)
                }
                
                val orders = _manualTrackedOrders.value
                var yPos = 160f
                canvas.drawText("Ordres Identifiés:", 50f, yPos, paintTitle)
                yPos += 20f
                
                val headers = if (kin.isEnabled) "Ordre | Plage RPM | Plage Vitesse (km/h) | Plage Fréq (Hz) | Max Émergence (TTNR)" 
                              else "Ordre | Plage Fréq (Hz) | Max Émergence (TTNR)"
                canvas.drawText(headers, 50f, yPos, paintText)
                yPos += 15f
                canvas.drawLine(50f, yPos, 545f, yPos, paintText)
                yPos += 15f
                
                for (order in orders) {
                    val line = if (kin.isEnabled) {
                        "\ | \ - \ RPM | " +
                        "\ - \ km/h | " +
                        "\ - \ Hz | " +
                        "\ dB"
                    } else {
                        "\ | \ - \ Hz | " +
                        "\ dB"
                    }
                    canvas.drawText(line, 50f, yPos, paintText)
                    yPos += 15f
                }
                
                pdfDocument.finishPage(page)
                
                // Save logic
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Rapport_NVH_\.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NVHSpectro_Reports")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outStream ->
                        pdfDocument.writeTo(outStream)
                    }
                }
                pdfDocument.close()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
'''

last_brace_idx = content.rfind('}')
if last_brace_idx != -1:
    new_content = content[:last_brace_idx] + PDF_METHOD + "\n}\n"
    with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("PDF method added.")
