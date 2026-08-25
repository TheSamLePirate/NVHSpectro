import re

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

start_str = '    private fun recalculateSmartPath() {'
end_str = '    fun validateCurrentOrder(customName: String? = null) {'

start_idx = text.find(start_str)
end_idx = text.find(end_str)

if start_idx == -1 or end_idx == -1:
    print('Could not find bounds.')
    exit(1)

new_func = '''    private fun recalculateSmartPath() {
        val points = _currentUserPoints.value
        if (points.size < 2) {
            _currentSmartPath.value = emptyList()
            return
        }

        // Le rayon de recherche permet a l algo de fouiller autour de la courbe tracee
        val maxSearchRadius = 15 
        
        val isTTNR = _displayMode.value == DisplayMode.TTNR
        val historyToUse = if (isTTNR) _fftHistoryTTNR.value else _fftHistoryAbsolute.value
        if (historyToUse.isEmpty()) return

        val startFrame = points.first().frameIndex.coerceIn(0, historyToUse.size - 1)
        val endFrame = points.last().frameIndex.coerceIn(0, historyToUse.size - 1)
        
        if (startFrame >= endFrame) {
            _currentSmartPath.value = points
            return
        }
        
        val numFrames = endFrame - startFrame + 1
        val binCount = historyToUse[startFrame].size
        
        val dpScores = Array(numFrames) { FloatArray(binCount) { -Float.MAX_VALUE } }
        val backPointers = Array(numFrames) { IntArray(binCount) { -1 } }
        
        // La ligne directrice (Spline/Cosinus) basee sur les points de l utilisateur
        fun getExpectedBin(globalFrame: Int): Int {
            if (globalFrame <= points.first().frameIndex) return points.first().binIndex
            if (globalFrame >= points.last().frameIndex) return points.last().binIndex
            for (i in 0 until points.size - 1) {
                if (globalFrame >= points[i].frameIndex && globalFrame <= points[i+1].frameIndex) {
                    val f1 = points[i].frameIndex
                    val b1 = points[i].binIndex
                    val f2 = points[i+1].frameIndex
                    val b2 = points[i+1].binIndex
                    if (f1 == f2) return b1
                    val fraction = (globalFrame - f1).toFloat() / (f2 - f1).toFloat()
                    // Interpolation Cosinus pour une courbe de base naturelle
                    val mu2 = (1.0 - Math.cos(fraction * Math.PI)) / 2.0
                    return Math.round(b1 * (1.0 - mu2) + b2 * mu2).toInt()
                }
            }
            return points.last().binIndex
        }

        // Pre-calcul de l energie en dB pour chaque frame et bin
        val dbEnergies = Array(numFrames) { FloatArray(binCount) }
        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val spectrum = historyToUse[globalFrame]
            for (b in 0 until binCount) {
                val raw = spectrum[b]
                dbEnergies[f][b] = if (raw > 0.0) (10.0 * Math.log10(raw)).toFloat() else -100f
            }
        }

        // Fonction pour calculer la "Prominence" (a quel point un pic ressort par rapport au bruit local)
        fun getProminence(f: Int, b: Int): Float {
            var sum = 0f
            var count = 0
            for (i in -3..3) {
                val neighbor = b + i
                if (neighbor in 0 until binCount) {
                    sum += dbEnergies[f][neighbor]
                    count++
                }
            }
            val avg = sum / count
            return Math.max(0f, dbEnergies[f][b] - avg)
        }

        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val expectedBin = getExpectedBin(globalFrame)
            val minBin = (expectedBin - maxSearchRadius).coerceAtLeast(0)
            val maxBin = (expectedBin + maxSearchRadius).coerceAtMost(binCount - 1)
            
            for (b in minBin..maxBin) {
                val energyDb = dbEnergies[f][b]
                val prominence = getProminence(f, b)
                
                // Score intrinseque de ce point (independant du point precedent)
                // 1. On adore les pics locaux (prominence)
                // 2. On aime bien l energie globale
                // 3. On penalise quadratiquement l eloignement de la ligne dessinee par l utilisateur
                val distToExpected = Math.abs(b - expectedBin).toFloat()
                val guidePenalty = 0.5f * distToExpected * distToExpected
                val nodeScore = (3.0f * prominence) + (0.5f * energyDb) - guidePenalty
                
                if (f == 0) {
                    dpScores[0][b] = nodeScore
                } else {
                    val prevExpected = getExpectedBin(globalFrame - 1)
                    val prevMin = (prevExpected - maxSearchRadius).coerceAtLeast(0)
                    val prevMax = (prevExpected + maxSearchRadius).coerceAtMost(binCount - 1)
                    
                    var bestScore = -Float.MAX_VALUE
                    var bestPrevBin = -1
                    
                    for (prevB in prevMin..prevMax) {
                        val prevScore = dpScores[f-1][prevB]
                        if (prevScore > -Float.MAX_VALUE) {
                            val jumpDistance = Math.abs(b - prevB).toFloat()
                            // Forte penalite quadratique sur les sauts brusques pour forcer une courbe ultra-lisse
                            val jumpPenalty = 2.0f * jumpDistance * jumpDistance
                            
                            val score = prevScore + nodeScore - jumpPenalty
                            
                            if (score > bestScore) {
                                bestScore = score
                                bestPrevBin = prevB
                            }
                        }
                    }
                    dpScores[f][b] = bestScore
                    backPointers[f][b] = bestPrevBin
                }
            }
        }
        
        // Find best end point
        val endExpected = getExpectedBin(endFrame)
        val endMin = (endExpected - maxSearchRadius).coerceAtLeast(0)
        val endMax = (endExpected + maxSearchRadius).coerceAtMost(binCount - 1)
        
        var bestEndScore = -Float.MAX_VALUE
        var bestEndBin = endExpected
        for (b in endMin..endMax) {
            if (dpScores[numFrames - 1][b] > bestEndScore) {
                bestEndScore = dpScores[numFrames - 1][b]
                bestEndBin = b
            }
        }
        
        // Backtrack
        val rawPath = mutableListOf<ManualOrderAnchor>()
        var currentBin = bestEndBin
        for (f in numFrames - 1 downTo 0) {
            val globalFrame = startFrame + f
            val isUserPoint = points.any { it.frameIndex == globalFrame }
            
            // On ne force plus la ligne a passer pile par le point,
            // mais on garde visuellement le fait que c est a ce moment la que l utilisateur a pose un point
            rawPath.add(ManualOrderAnchor(globalFrame, currentBin, isUserPlaced = isUserPoint))
            
            if (currentBin != -1) {
                currentBin = backPointers[f][currentBin]
            }
            if (currentBin == -1 && f > 0) {
                currentBin = getExpectedBin(startFrame + f - 1) // Fallback
            }
        }
        
        rawPath.reverse()
        _currentSmartPath.value = rawPath
    }

'''

new_text = text[:start_idx] + new_func + text[end_idx:]
with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated MainViewModel.kt with intelligent tracking')
