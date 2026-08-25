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

        // Rayon tres large : le point utilisateur est un guide, l'algo peut chercher l'harmo jusqu'a 30 pixels autour
        val maxSearchRadius = 30 
        
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
        
        // Ligne de guide : stricte ligne droite (lineaire) entre les points pour donner la PENTE generale
        fun getExpectedBinF(globalFrame: Int): Float {
            if (globalFrame <= points.first().frameIndex) return points.first().binIndex.toFloat()
            if (globalFrame >= points.last().frameIndex) return points.last().binIndex.toFloat()
            for (i in 0 until points.size - 1) {
                if (globalFrame >= points[i].frameIndex && globalFrame <= points[i+1].frameIndex) {
                    val f1 = points[i].frameIndex
                    val b1 = points[i].binIndex.toFloat()
                    val f2 = points[i+1].frameIndex
                    val b2 = points[i+1].binIndex.toFloat()
                    if (f1 == f2) return b1
                    val fraction = (globalFrame - f1).toFloat() / (f2 - f1).toFloat()
                    return b1 + fraction * (b2 - b1)
                }
            }
            return points.last().binIndex.toFloat()
        }

        // Energie en dB
        val dbEnergies = Array(numFrames) { FloatArray(binCount) }
        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val spectrum = historyToUse[globalFrame]
            for (b in 0 until binCount) {
                val raw = spectrum[b]
                dbEnergies[f][b] = if (raw > 0.0) (10.0 * Math.log10(raw)).toFloat() else -100f
            }
        }

        // Prominence : Detection pragmatique de la crete. On regarde +/- 4 pixels autour en ignorant le centre.
        fun getProminence(f: Int, b: Int): Float {
            var sum = 0f
            var count = 0
            for (i in -4..4) {
                if (Math.abs(i) <= 1) continue // Ignore le pic lui-meme pour avoir le vrai bruit de fond
                val neighbor = b + i
                if (neighbor in 0 until binCount) {
                    sum += dbEnergies[f][neighbor]
                    count++
                }
            }
            val avg = if (count > 0) sum / count else dbEnergies[f][b]
            return Math.max(0f, dbEnergies[f][b] - avg)
        }

        for (f in 0 until numFrames) {
            val globalFrame = startFrame + f
            val expectedBinF = getExpectedBinF(globalFrame)
            val expectedBinInt = Math.round(expectedBinF)
            
            val minBin = (expectedBinInt - maxSearchRadius).coerceAtLeast(0)
            val maxBin = (expectedBinInt + maxSearchRadius).coerceAtMost(binCount - 1)
            
            for (b in minBin..maxBin) {
                val energyDb = dbEnergies[f][b]
                val prominence = getProminence(f, b)
                
                // Penalite tres faible pour s'eloigner du guide. Ca autorise l'algo a aller chasser loin !
                val distToExpected = Math.abs(b.toFloat() - expectedBinF)
                val guidePenalty = 0.5f * distToExpected
                
                // On recompense MASSIVEMENT l'energie et surtout la prominence (les cretes)
                val nodeScore = (10.0f * prominence) + (1.0f * energyDb) - guidePenalty
                
                if (f == 0) {
                    dpScores[0][b] = nodeScore
                } else {
                    val prevExpectedF = getExpectedBinF(globalFrame - 1)
                    val expectedJumpF = expectedBinF - prevExpectedF
                    
                    val prevExpectedInt = Math.round(prevExpectedF)
                    val prevMin = (prevExpectedInt - maxSearchRadius).coerceAtLeast(0)
                    val prevMax = (prevExpectedInt + maxSearchRadius).coerceAtMost(binCount - 1)
                    
                    var bestScore = -Float.MAX_VALUE
                    var bestPrevBin = -1
                    
                    for (prevB in prevMin..prevMax) {
                        val prevScore = dpScores[f-1][prevB]
                        if (prevScore > -Float.MAX_VALUE) {
                            val actualJumpF = (b - prevB).toFloat()
                            val jumpDeviation = Math.abs(actualJumpF - expectedJumpF)
                            
                            // Penalite FORTE sur le changement de pente, pour garantir une courbe ULTRA LISSE !
                            val jumpPenalty = 5.0f * jumpDeviation * jumpDeviation
                            
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
        val endExpectedF = getExpectedBinF(endFrame)
        val endExpectedInt = Math.round(endExpectedF)
        val endMin = (endExpectedInt - maxSearchRadius).coerceAtLeast(0)
        val endMax = (endExpectedInt + maxSearchRadius).coerceAtMost(binCount - 1)
        
        var bestEndScore = -Float.MAX_VALUE
        var bestEndBin = endExpectedInt
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
            
            rawPath.add(ManualOrderAnchor(globalFrame, currentBin, isUserPlaced = isUserPoint))
            
            if (currentBin != -1) {
                currentBin = backPointers[f][currentBin]
            }
            if (currentBin == -1 && f > 0) {
                currentBin = Math.round(getExpectedBinF(startFrame + f - 1))
            }
        }
        
        // Smoothing final (moyenne glissante legere) pour enlever l'effet escalier d'un pixel
        val smoothedPath = mutableListOf<ManualOrderAnchor>()
        rawPath.reverse()
        val smoothingWindow = 3
        for (i in rawPath.indices) {
            var sumBin = 0
            var count = 0
            for (j in -smoothingWindow..smoothingWindow) {
                val idx = i + j
                if (idx in rawPath.indices) {
                    sumBin += rawPath[idx].binIndex
                    count++
                }
            }
            val avgBin = Math.round(sumBin.toFloat() / count)
            smoothedPath.add(ManualOrderAnchor(rawPath[i].frameIndex, avgBin, rawPath[i].isUserPlaced))
        }
        
        _currentSmartPath.value = smoothedPath
    }

'''

new_text = text[:start_idx] + new_func + text[end_idx:]
with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated MainViewModel.kt with pragmatic tracking')
