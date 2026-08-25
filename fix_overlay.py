import os

file_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

old_overlay = '''            // --- DESSIN DES OVERLAYS DE FILTRES AUDIO (Bandes Semi-Transparentes) ---
            if (activeFilters.isNotEmpty()) {
                val filterFillPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val filterStrokePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
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
                    filterFillPaint.alpha = 110 // ~43% opaque
                    
                    filterStrokePaint.color = baseColor
                    filterStrokePaint.alpha = 255'''

new_overlay = '''            // --- DESSIN DES OVERLAYS DE FILTRES AUDIO (Bandes Semi-Transparentes) ---
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
                    filterStrokePaint.setShadowLayer(8f, 0f, 0f, baseColor)'''

code = code.replace(old_overlay, new_overlay)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Fixed SpectrogramColormap.kt")
