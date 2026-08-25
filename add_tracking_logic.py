import re

with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Generate a list of available distinct colors
COLOR_POOL = [
    "Color(0xFF00BFFF)", # Deep Sky Blue
    "Color(0xFFFF1493)", # Deep Pink
    "Color(0xFF32CD32)", # Lime Green
    "Color(0xFFFFA500)", # Orange
    "Color(0xFF8A2BE2)", # Blue Violet
    "Color(0xFF00FFFF)", # Cyan
    "Color(0xFFFFD700)", # Gold
]

METHODS = '''
    fun toggleReportMode() {
        if (!_isReportModeActive.value) {
            if (_audioSourceMode.value == AudioSourceMode.LIVE) {
                _isFrozen.value = true
            }
            _isReportModeActive.value = true
        } else {
            _isReportModeActive.value = false
            _currentUserPoints.value = emptyList()
            _currentSmartPath.value = emptyList()
            // optionally resume
        }
    }

    fun clearCurrentSmartTrack() {
        _currentUserPoints.value = emptyList()
        _currentSmartPath.value = emptyList()
    }

    fun clearAllValidatedOrders() {
        _manualTrackedOrders.value = emptyList()
    }

    fun addManualTrackPoint(frameIndex: Int, binIndex: Int) {
        val currentPoints = _currentUserPoints.value.toMutableList()
        // If exact same point is tapped, maybe remove it? Or just add.
        currentPoints.add(ManualOrderAnchor(frameIndex, binIndex, isUserPlaced = true))
        
        // Sort by X axis (chronological)
        currentPoints.sortBy { it.frameIndex }
        _currentUserPoints.value = currentPoints
        
        recalculateSmartPath()
    }

    private fun recalculateSmartPath() {
        val points = _currentUserPoints.value
        if (points.size < 2) {
            _currentSmartPath.value = emptyList()
            return
        }

        val smartPath = mutableListOf<ManualOrderAnchor>()
        val searchRadius = 4 // Number of bins up/down to search for a local maximum

        // Which map to use for tracking? Let's use the one currently displayed.
        val historyToUse = if (_displayMode.value == DisplayMode.TTNR) _fftHistoryTTNR.value else _fftHistoryAbsolute.value
        if (historyToUse.isEmpty()) return

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            
            val startFrame = p1.frameIndex.coerceIn(0, historyToUse.size - 1)
            val endFrame = p2.frameIndex.coerceIn(0, historyToUse.size - 1)
            
            if (startFrame == endFrame) {
                smartPath.add(p1)
                continue
            }
            
            var previousBin = p1.binIndex
            
            for (f in startFrame..endFrame) {
                // Determine the linear interpolated expected bin
                val fraction = (f - startFrame).toFloat() / (endFrame - startFrame)
                val expectedBin = Math.round(p1.binIndex + fraction * (p2.binIndex - p1.binIndex)).toInt()
                
                // If it is one of the anchor points, force it exactly
                if (f == startFrame) {
                    smartPath.add(p1)
                    previousBin = p1.binIndex
                    continue
                }
                if (f == endFrame) {
                    // Do not add endFrame here, it will be added as startFrame in the next loop, or at the very end
                    break
                }
                
                val currentSpectrum = historyToUse[f]
                
                // We want to snap to the maximum energy near the expected line, BUT we don't want it to jump.
                // So we search around the 'previousBin' rather than just 'expectedBin', but we bias towards expected.
                // A simple approach: search window around expectedBin.
                val searchCenter = (expectedBin + previousBin) / 2
                val minSearchBin = (searchCenter - searchRadius).coerceAtLeast(0)
                val maxSearchBin = (searchCenter + searchRadius).coerceAtMost(currentSpectrum.size - 1)
                
                var bestBin = searchCenter
                var maxEnergy = -1000.0
                
                for (b in minSearchBin..maxSearchBin) {
                    if (currentSpectrum[b] > maxEnergy) {
                        maxEnergy = currentSpectrum[b]
                        bestBin = b
                    }
                }
                
                smartPath.add(ManualOrderAnchor(f, bestBin, isUserPlaced = false))
                previousBin = bestBin
            }
        }
        
        // Add the very last point
        smartPath.add(points.last())
        
        _currentSmartPath.value = smartPath
    }

    fun validateCurrentOrder() {
        val path = _currentSmartPath.value
        if (path.isEmpty()) return
        
        val absHistory = _fftHistoryAbsolute.value
        val ttnrHistory = _fftHistoryTTNR.value
        val telemHistory = _telemetryHistory.value
        val sampleRate = _loadedWavData.value?.sampleRate ?: 44100
        val nyquist = sampleRate / 2.0
        val totalBins = if (absHistory.isNotEmpty()) absHistory[0].size else 2048
        val df = nyquist / totalBins

        var minRpm: Int? = null
        var maxRpm: Int? = null
        var minSpeed: Float? = null
        var maxSpeed: Float? = null
        var minFreqHz = Int.MAX_VALUE
        var maxFreqHz = Int.MIN_VALUE
        var maxEmergence = -100.0

        for (anchor in path) {
            val f = anchor.frameIndex
            if (f in ttnrHistory.indices) {
                val b = anchor.binIndex.coerceIn(0, totalBins - 1)
                val freqHz = (b * df).toInt()
                if (freqHz < minFreqHz) minFreqHz = freqHz
                if (freqHz > maxFreqHz) maxFreqHz = freqHz
                
                val emergence = ttnrHistory[f][b]
                if (emergence > maxEmergence) maxEmergence = emergence
            }
            
            if (f in telemHistory.indices) {
                val telem = telemHistory[f]
                val speed = if (_kinematicsConfig.value.isEnabled) telem.theoreticalSpeedKmh else telem.speedKmh
                if (speed > 1.0f) {
                    if (minSpeed == null || speed < minSpeed) minSpeed = speed
                    if (maxSpeed == null || speed > maxSpeed) maxSpeed = speed
                    
                    val rpm = _kinematicsConfig.value.calculateRpm(speed).toInt()
                    if (rpm > 100) {
                        if (minRpm == null || rpm < minRpm) minRpm = rpm
                        if (maxRpm == null || rpm > maxRpm) maxRpm = rpm
                    }
                }
            }
        }

        if (minFreqHz == Int.MAX_VALUE) minFreqHz = 0
        if (maxFreqHz == Int.MIN_VALUE) maxFreqHz = 0

        // Determine name and color
        val count = _manualTrackedOrders.value.size
        val name = "Tracé \"
        
        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFF00BFFF), // Deep Sky Blue
            androidx.compose.ui.graphics.Color(0xFFFF1493), // Deep Pink
            androidx.compose.ui.graphics.Color(0xFF32CD32), // Lime Green
            androidx.compose.ui.graphics.Color(0xFFFFA500), // Orange
            androidx.compose.ui.graphics.Color(0xFF8A2BE2), // Blue Violet
            androidx.compose.ui.graphics.Color(0xFF00FFFF), // Cyan
            androidx.compose.ui.graphics.Color(0xFFFFD700)  // Gold
        )
        val color = colors[count % colors.size]

        val order = SmartTrackedOrder(
            name = name,
            color = color,
            path = path,
            minRpm = minRpm,
            maxRpm = maxRpm,
            minSpeedKmh = minSpeed,
            maxSpeedKmh = maxSpeed,
            minFreqHz = minFreqHz,
            maxFreqHz = maxFreqHz,
            maxEmergenceDb = maxEmergence
        )

        _manualTrackedOrders.value = _manualTrackedOrders.value + order
        clearCurrentSmartTrack()
    }
'''

# We need to insert METHODS before the last closing brace.
last_brace_idx = content.rfind('}')
if last_brace_idx != -1:
    new_content = content[:last_brace_idx] + METHODS + "\n}\n"
    with open('app/src/main/java/com/example/nvhspectro/MainViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("MainViewModel updated successfully.")
else:
    print("Error: Could not find closing brace.")
