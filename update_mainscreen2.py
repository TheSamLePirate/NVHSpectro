import re

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

spectro_canvas_old = '''                    SpectrogramCanvas(
                        history = fftHistory,
                        absHistory = fftHistoryAbsolute,
                        ttnrHistory = fftHistoryTTNR,
                        minDb = minDb,
                        maxDb = maxDb,
                        minFreq = minFreq,
                        maxFreq = maxFreq,
                        fftSize = fftSize,
                        sampleRate = 44100,
                        historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                        displayMode = displayMode,
                        isDetectorEnabled = isDetectorEnabled,
                        emergenceThresholdDb = emergenceThresholdDb,
                        magnitudeGateDbFS = magnitudeGateDbFS,
                        trackedHarmonicTags = trackedHarmonicTags,
                        activeFilters = activeFilters,
                        kinematicsConfig = kinematicsConfig,
                        isWavAnalyzerMode = isWavMode,
                        wavPlaybackProgress = wavProgress,
                        showH1Overlay = viewModel.showH1Overlay.collectAsState().value,
                        projectedOrder = viewModel.projectedOrder.collectAsState().value,
                        telemetryHistory = telemetryHistory
                    )'''
spectro_canvas_new = '''                    SpectrogramCanvas(
                        history = fftHistory,
                        absHistory = fftHistoryAbsolute,
                        ttnrHistory = fftHistoryTTNR,
                        minDb = minDb,
                        maxDb = maxDb,
                        minFreq = minFreq,
                        maxFreq = maxFreq,
                        fftSize = fftSize,
                        sampleRate = 44100,
                        historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                        displayMode = displayMode,
                        isDetectorEnabled = isDetectorEnabled,
                        emergenceThresholdDb = emergenceThresholdDb,
                        magnitudeGateDbFS = magnitudeGateDbFS,
                        trackedHarmonicTags = trackedHarmonicTags,
                        activeFilters = activeFilters,
                        kinematicsConfig = kinematicsConfig,
                        isWavAnalyzerMode = isWavMode,
                        wavPlaybackProgress = wavProgress,
                        showH1Overlay = viewModel.showH1Overlay.collectAsState().value,
                        projectedOrder = viewModel.projectedOrder.collectAsState().value,
                        telemetryHistory = telemetryHistory,
                        isReportModeActive = isReportModeActive,
                        currentUserPoints = currentUserPoints,
                        currentSmartPath = currentSmartPath,
                        manualTrackedOrders = manualTrackedOrders,
                        onAddManualPoint = { fIdx, bIdx -> viewModel.addManualTrackPoint(fIdx, bIdx) }
                    )'''
content = content.replace(spectro_canvas_old, spectro_canvas_new)

panel_composable = '''
@Composable
fun ManualReportControlsPanel(viewModel: MainViewModel) {
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(8.dp)
    ) {
        Text("Mode Rapport Actif", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Selectionnez des points sur le spectrogramme pour tracer un ordre.", color = Color.LightGray, fontSize = 12.sp)
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { viewModel.validateCurrentOrder() },
                enabled = currentUserPoints.size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Valider l'Ordre", color = Color.White)
            }
            
            Button(
                onClick = { viewModel.clearCurrentSmartTrack() },
                enabled = currentUserPoints.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) {
                Text("Effacer Trace", color = Color.White)
            }
        }
        
        if (manualTrackedOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ordres traces: \", color = Color.White, fontWeight = FontWeight.Bold)
            manualTrackedOrders.forEach { order ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(16.dp).background(order.color))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(order.name, color = Color.White)
                }
            }
            
            Button(
                onClick = { /* TODO PDF EXPORT */ },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Generer PDF", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
'''
content = content + panel_composable

box_old = '''                // Spectrogram Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    SpectrogramCanvas('''
box_new = '''                // Spectrogram Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isReportModeActive) 1.0f else 0.55f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    SpectrogramCanvas('''
content = content.replace(box_old, box_new)

canvas_call_end = '''                        onAddManualPoint = { fIdx, bIdx -> viewModel.addManualTrackPoint(fIdx, bIdx) }
                    )'''
canvas_call_new = canvas_call_end + '''
                    
                    if (isReportModeActive) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            ManualReportControlsPanel(viewModel)
                        }
                    }'''
content = content.replace(canvas_call_end, canvas_call_new)

telemetry_old = '''                // Bottom Graph Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                ) {'''
telemetry_new = '''                // Bottom Graph Section
                if (!isReportModeActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    ) {'''
content = content.replace(telemetry_old, telemetry_new)

with open('app/src/main/java/com/example/nvhspectro/MainScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("MainScreen updated.")
