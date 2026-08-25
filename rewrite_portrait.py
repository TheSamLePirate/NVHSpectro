import os

new_content = '''package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.MainViewModel
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.AudioSourceMode

@Composable
fun ReportModeScreen(viewModel: MainViewModel) {
    // Collect SNAPSHOT history
    val fftHistory by viewModel.reportFftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.reportFftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.reportFftHistoryTTNR.collectAsState()

    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    
    val isDetectorEnabled by viewModel.isDetectorEnabled.collectAsState()
    val emergenceThresholdDb by viewModel.emergenceThresholdDb.collectAsState()
    val magnitudeGateDbFS by viewModel.magnitudeGateDbFS.collectAsState()
    
    val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val trackedHarmonicTags by viewModel.trackedHarmonicTags.collectAsState()
    val audioSourceMode by viewModel.audioSourceMode.collectAsState()
    val isWavAnalyzerMode = (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER || audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO)
    
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val currentSmartPath by viewModel.currentSmartPath.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()
    
    // UI state
    val isDrawingMode by viewModel.isDrawingMode.collectAsState()
    val sampleRate = 44100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // TOP 55%: Spectrogram
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .background(Color.Black)
        ) {
            SpectrogramCanvas(
                history = fftHistory,
                absHistory = fftHistoryAbsolute,
                ttnrHistory = fftHistoryTTNR,
                minDb = minDb,
                maxDb = maxDb,
                minFreq = minFreq,
                maxFreq = maxFreq,
                fftSize = fftSize,
                sampleRate = sampleRate,
                historySize = if (isWavAnalyzerMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                displayMode = displayMode,
                isDetectorEnabled = isDetectorEnabled,
                emergenceThresholdDb = emergenceThresholdDb,
                magnitudeGateDbFS = magnitudeGateDbFS,
                trackedHarmonicTags = trackedHarmonicTags,
                activeFilters = activeFilters,
                kinematicsConfig = kinematicsConfig,
                isWavAnalyzerMode = isWavAnalyzerMode,
                wavPlaybackProgress = 0f, // No progress bar in report mode usually
                isReportModeActive = true,
                isDrawingMode = isDrawingMode, // Pan/zoom disabled if drawing mode is ON
                currentUserPoints = currentUserPoints,
                currentSmartPath = currentSmartPath,
                manualTrackedOrders = manualTrackedOrders,
                onAddManualPoint = { frameIdx, binIdx ->
                    if (isDrawingMode) {
                        viewModel.addManualTrackPoint(frameIdx, binIdx)
                    }
                }
            )
            
            // Mode selectors top-left over spectrogram
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DisplayMode.values().forEach { mode ->
                    FilterChip(
                        selected = (displayMode == mode),
                        onClick = { viewModel.setDisplayMode(mode) },
                        label = { Text(mode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = Color(0xDD1E2430),
                            labelColor = Color(0xFFE0E0E0)
                        )
                    )
                }
            }
        }

        Divider(color = Color.DarkGray, thickness = 2.dp)

        // BOTTOM 45%: Controls and List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .padding(8.dp)
        ) {
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleDrawingMode() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDrawingMode) Color(0xFFE53935) else Color(0xFF424242)
                    )
                ) {
                    Text(
                        text = if (isDrawingMode) "✅ Placer Points" else "🎯 Placer Points",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { viewModel.clearCurrentPoints() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("🗑️ Effacer", color = Color.White, fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.validateCurrentOrder() },
                    modifier = Modifier.weight(1f),
                    enabled = currentSmartPath.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("💾 Valider", color = Color.White, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // List of Orders
            Text("Ordres Validés ()", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
            ) {
                if (manualTrackedOrders.isEmpty()) {
                    Text(
                        "Aucun ordre validé.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 12.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(manualTrackedOrders) { order ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2D2D2D), shape = MaterialTheme.shapes.small)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Ordre (Moy): ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Points: ", color = Color.Gray, fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { viewModel.removeValidatedOrder(order) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("X", color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Main Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleReportMode() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1)) // Deep Purple
                ) {
                    Text("⬅️ Retour Live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.generatePdfReport(null) },
                    modifier = Modifier.weight(1f),
                    enabled = manualTrackedOrders.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)) // Blue
                ) {
                    Text("📄 Générer PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
'''

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
