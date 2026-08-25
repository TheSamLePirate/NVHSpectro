import os

file_path = r'app\src\main\java\com\example\nvhspectro\SpectrogramColormap.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Add import
code = code.replace(
    'import com.example.nvhspectro.data.TrackedHarmonicTag',
    'import com.example.nvhspectro.data.TrackedHarmonicTag\nimport com.example.nvhspectro.data.AudioFilter\nimport com.example.nvhspectro.data.FilterType'
)

# Add parameter to SpectrogramCanvas
old_params = '''    projectedOrder: Double = 1.0,
    telemetryHistory: List<TelemetryData> = emptyList()
) {'''
new_params = '''    projectedOrder: Double = 1.0,
    telemetryHistory: List<TelemetryData> = emptyList(),
    activeFilters: List<AudioFilter> = emptyList()
) {'''
code = code.replace(old_params, new_params)

# Add overlay drawing logic
overlay_logic = '''
            // --- DESSIN DES OVERLAYS DE FILTRES AUDIO (Bandes Semi-Transparentes) ---
            if (activeFilters.isNotEmpty()) {
                val filterPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                
                for (filter in activeFilters) {
                    filterPaint.color = filter.color.toArgb()
                    filterPaint.alpha = 50 // Semi-transparent (environ 20% d'opacité)
                    
                    val fMin = filter.minFreq.toFloat()
                    val fMax = filter.maxFreq.toFloat()
                    
                    // Fonction locale pour calculer la coordonnée Y d'une fréquence
                    fun getFreqY(freqHz: Float): Float {
                        if (freqHz <= actualMinFreq) return plotBottom
                        if (freqHz >= actualMaxFreq) return marginTop
                        val fraction = (freqHz - actualMinFreq) / (actualMaxFreq - actualMinFreq)
                        return plotBottom - (fraction * plotHeight)
                    }

                    when (filter.type) {
                        FilterType.LOW_PASS -> {
                            val yTop = marginTop
                            val yBottom = getFreqY(fMax)
                            if (yBottom > yTop) {
                                native.drawRect(marginLeft, yTop, plotRight, yBottom, filterPaint)
                            }
                        }
                        FilterType.HIGH_PASS -> {
                            val yTop = getFreqY(fMin)
                            val yBottom = plotBottom
                            if (yBottom > yTop) {
                                native.drawRect(marginLeft, yTop, plotRight, yBottom, filterPaint)
                            }
                        }
                        FilterType.BAND_PASS -> {
                            // En passe-bande, on rejette l'extérieur. 
                            // 1. Bande supérieure (au-dessus de maxFreq)
                            val yTop1 = marginTop
                            val yBottom1 = getFreqY(fMax)
                            if (yBottom1 > yTop1) {
                                native.drawRect(marginLeft, yTop1, plotRight, yBottom1, filterPaint)
                            }
                            // 2. Bande inférieure (en-dessous de minFreq)
                            val yTop2 = getFreqY(fMin)
                            val yBottom2 = plotBottom
                            if (yBottom2 > yTop2) {
                                native.drawRect(marginLeft, yTop2, plotRight, yBottom2, filterPaint)
                            }
                        }
                        FilterType.BAND_STOP -> {
                            val yTop = getFreqY(fMax)
                            val yBottom = getFreqY(fMin)
                            if (yBottom > yTop) {
                                native.drawRect(marginLeft, yTop, plotRight, yBottom, filterPaint)
                            }
                        }
                    }
                }
            }
'''

code = code.replace(
    '            // --- DESSIN DES BALISES CLIGNOTANTES',
    overlay_logic + '\n            // --- DESSIN DES BALISES CLIGNOTANTES'
)

# Convert Jetpack Color to Android Color inside the Canvas
code = code.replace(
    'filter.color.toArgb()',
    'android.graphics.Color.argb((filter.color.alpha * 255).toInt(), (filter.color.red * 255).toInt(), (filter.color.green * 255).toInt(), (filter.color.blue * 255).toInt())'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated SpectrogramColormap.kt")
