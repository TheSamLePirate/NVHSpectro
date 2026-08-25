import os

file_path = r'app\src\main\java\com\example\nvhspectro\FFTProcessor.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

old_block = code[code.find('fun computeTTNR'):code.find('return finalTtnr') + len('return finalTtnr\n    }')]

new_block = '''fun computeTTNR(magnitudesDbFS: DoubleArray, sampleRate: Int = 44100): DoubleArray {
        val binCount = magnitudesDbFS.size
        val df = sampleRate.toDouble() / fftSize
        val rawTtnr = DoubleArray(binCount) { -100.0 }

        // Convertir dBFS en puissance lineaire
        val powerLinear = DoubleArray(binCount) { i ->
            Math.pow(10.0, magnitudesDbFS[i] / 10.0)
        }

        // Filtre de Forme (Shape Filter) : On ne calcule le TTNR que pour les "vrais" pics spectraux
        // Un pic sinusoïdal fenêtré par Hanning a ses voisins immediats au moins 1.5 dB sous la crête.
        val isTruePeak = BooleanArray(binCount)
        for (i in 2 until binCount - 2) {
            val f = i * df
            if (f < 30.0 || f > 10000.0) continue

            val valCurr = magnitudesDbFS[i]
            val valPrev = magnitudesDbFS[i - 1]
            val valNext = magnitudesDbFS[i + 1]

            // Filtre de forme stricte : le pic doit s'extraire de ses voisins de +1.0 dB minimum
            if (valCurr > valPrev + 1.0 && valCurr > valNext + 1.0) {
                isTruePeak[i] = true
            }
        }

        // Parcours pour le calcul du TTNR
        for (i in 2 until binCount - 2) {
            if (!isTruePeak[i]) continue
            
            val f = i * df
            
            // Critical Bandwidth (ECMA-74)
            val cb = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * (f / 1000.0).pow(2), 0.69)
            val cbBins = maxOf(3, (cb / df).toInt())
            val halfCbBins = cbBins / 2

            val minBin = maxOf(0, i - halfCbBins)
            val maxBin = minOf(binCount - 1, i + halfCbBins)

            var pNoiseSum = 0.0
            var noiseCount = 0
            var pToneGross = 0.0

            for (j in minBin..maxBin) {
                if (Math.abs(j - i) > 1) {
                    pNoiseSum += powerLinear[j]
                    noiseCount++
                } else {
                    pToneGross += powerLinear[j]
                }
            }

            if (noiseCount > 0) {
                val pNoiseDensityPerHz = pNoiseSum / (noiseCount * df)
                val pNoiseTotalCb = pNoiseDensityPerHz * cb
                val pToneNet = pToneGross - (pNoiseDensityPerHz * 3.0 * df)

                if (pToneNet > 0.0) {
                    val ttnrCbDb = 10.0 * kotlin.math.log10(pToneNet / pNoiseTotalCb)
                    val localNoiseFloorDbFS = 10.0 * kotlin.math.log10(pNoiseDensityPerHz * df)
                    val localEmergenceDb = (magnitudesDbFS[i] - localNoiseFloorDbFS)
                    
                    // Seuil minimal abaissé pour capter les micro-émergences
                    if (localEmergenceDb >= -2.0) {
                        val finalPeakTtnr = maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(-100.0, 30.0)
                        
                        rawTtnr[i] = finalPeakTtnr
                        
                        // Reconstitution géométrique du pic (leakage -6 dB)
                        if (rawTtnr[i - 1] < finalPeakTtnr - 6.0) rawTtnr[i - 1] = finalPeakTtnr - 6.0
                        if (rawTtnr[i + 1] < finalPeakTtnr - 6.0) rawTtnr[i + 1] = finalPeakTtnr - 6.0
                    }
                }
            }
        }

        // Lissage EMA temporel (reduit car on utilise deja Welch en amont)
        val alpha = 0.36
        val finalTtnr = DoubleArray(binCount)
        val prevIntegrated = integratedTtnr

        if (prevIntegrated != null && prevIntegrated.size == binCount) {
            for (i in 0 until binCount) {
                // On s'assure que si rawTtnr vaut -100, l'EMA descend vite
                val integVal = (1.0 - alpha) * prevIntegrated[i] + alpha * rawTtnr[i]
                finalTtnr[i] = integVal
            }
        } else {
            System.arraycopy(rawTtnr, 0, finalTtnr, 0, binCount)
        }

        integratedTtnr = finalTtnr.clone()
        return finalTtnr
    }'''

code = code.replace(old_block, new_block)

# Remove math.pow/log10 import errors if any, use kotlin.math.*
code = code.replace('Math.pow', 'kotlin.math.pow').replace('Math.abs', 'kotlin.math.abs').replace('Math.max', 'kotlin.math.max')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated FFTProcessor.kt with Shape Filter")
