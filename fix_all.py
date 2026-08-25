import os
content = '''package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.nvhspectro.SpectrogramCanvas

@Composable
fun ReportModeScreen(viewModel: MainViewModel) {
    val fftHistory by viewModel.fftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.fftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.fftHistoryTTNR.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val isDetectorEnabled by viewModel.isDetectorEnabled.collectAsState()
    val emergenceThresholdDb by viewModel.emergenceThresholdDb.collectAsState()
    val magnitudeGateDbFS by viewModel.magnitudeGateDbFS.collectAsState()
    val trackedHarmonicTags by viewModel.trackedHarmonicTags.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()
    val isAudioRecording by viewModel.isAudioRecording.collectAsState()
    val recordingElapsedSec by viewModel.recordingElapsedSec.collectAsState()
    
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()
    val selectedValidatedOrder by viewModel.selectedValidatedOrder.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(4.dp)
    ) {
        // En-tête (Top Row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sélecteur de Mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (displayMode == DisplayMode.ABSOLUTE) Color(0xFFB3C5FF) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (displayMode == DisplayMode.ABSOLUTE) Color(0xFFB3C5FF) else Color.Gray),
                    modifier = Modifier.clickable { viewModel.setDisplayMode(DisplayMode.ABSOLUTE) }
                ) {
                    Text("Absolue (dBFS)", color = if (displayMode == DisplayMode.ABSOLUTE) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (displayMode == DisplayMode.TTNR) Color(0xFFB3C5FF) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (displayMode == DisplayMode.TTNR) Color(0xFFB3C5FF) else Color.Gray),
                    modifier = Modifier.clickable { viewModel.setDisplayMode(DisplayMode.TTNR) }
                ) {
                    Text("TTNR (Emergence)", color = if (displayMode == DisplayMode.TTNR) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Info GMPe
            if (kinematicsConfig.isEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8EAF6),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9FA8DA))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Etude GMPe active", color = Color(0xFF7986CB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(f"{kinematicsConfig.vehicleName} ; {kinematicsConfig.motorName} ; {String.format(java.util.Locale.US, \"%.1f\", kinematicsConfig.getEffectiveV1000())}".replace("f", "$", 1), color = Color(0xFF5C6BC0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Zone Centrale (Colormap + Sidebar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Colormap (occupies most of the width)
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
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
                    sampleRate = 44100,
                    historySize = if (fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                    displayMode = displayMode,
                    isDetectorEnabled = isDetectorEnabled,
                    emergenceThresholdDb = emergenceThresholdDb,
                    magnitudeGateDbFS = magnitudeGateDbFS,
                    trackedHarmonicTags = trackedHarmonicTags,
                    activeFilters = activeFilters,
                    kinematicsConfig = kinematicsConfig,
                    isWavAnalyzerMode = false,
                    wavPlaybackProgress = 0f,
                    showH1Overlay = false,
                    projectedOrder = 1.0,
                    telemetryHistory = emptyList()
                )
            }

            // Sidebar (occupies remaining width)
            Column(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Liste Ordre
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(2.dp, Color.Red, RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Column {
                        Text("liste ordre", color = Color.Red, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(manualTrackedOrders) { order ->
                                val isSelected = (order == selectedValidatedOrder)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { viewModel.selectValidatedOrder(order) },
                                    color = if (isSelected) Color(0xFFD32F2F) else Color.DarkGray,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = order.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Boutons d\'action latéraux
                Button(
                    onClick = { viewModel.validateCurrentOrder() },
                    enabled = currentUserPoints.size >= 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAEEA00)),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("valider ordre", color = Color.Black, fontSize = 11.sp, maxLines = 1)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50)),
                    color = Color.Transparent
                ) {
                    Text(
                        text = "{currentUserPoints.size} points tracés".replace("{", "

                Button(
                    onClick = { viewModel.clearCurrentPoints() },
                    enabled = currentUserPoints.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("suprime points", color = Color.Black, fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = { 
                        selectedValidatedOrder?.let { viewModel.removeValidatedOrder(it) }
                    },
                    enabled = selectedValidatedOrder != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("supprime ordre", color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // Bottom Row
        val localContext = androidx.compose.ui.platform.LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.generatePdfReport(localContext) },
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF29B6F6))
            ) {
                Text("export PDF", color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.toggleReportMode() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800))
            ) {
                Text("Quitte rapport", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
            }
        }
    }
}
'''
with open("app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt", "w", encoding="utf-8") as f:
    f.write(content)
