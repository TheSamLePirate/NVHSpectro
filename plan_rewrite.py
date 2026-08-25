import sys

kotlin_code = '''package com.example.nvhspectro.ui

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.nvhspectro.MainViewModel
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.SpectrogramCanvas
import com.example.nvhspectro.HarmonicTag
import com.example.nvhspectro.AudioFilterConfig
import com.example.nvhspectro.KinematicsConfig
import com.example.nvhspectro.TrackedOrder
import com.example.nvhspectro.PointF

@Composable
fun ReportModeScreen(viewModel: MainViewModel) {
    val fftHistory by viewModel.reportFftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.reportFftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.reportFftHistoryTTNR.collectAsState()
    
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
    
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()
    val selectedValidatedOrder by viewModel.selectedValidatedOrder.collectAsState()
    val isDrawingMode by viewModel.isDrawingMode.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val localContext = LocalContext.current

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1A))
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SpectrogramArea(viewModel, fftHistory, fftHistoryAbsolute, fftHistoryTTNR, displayMode, minDb, maxDb, minFreq, maxFreq, fftSize, isDetectorEnabled, emergenceThresholdDb, magnitudeGateDbFS, trackedHarmonicTags, activeFilters, kinematicsConfig, isDrawingMode, currentUserPoints, manualTrackedOrders)
            }
            Box(modifier = Modifier.width(340.dp).fillMaxHeight()) {
                HudPanel(viewModel, currentUserPoints, manualTrackedOrders, selectedValidatedOrder, localContext)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1A))
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SpectrogramArea(viewModel, fftHistory, fftHistoryAbsolute, fftHistoryTTNR, displayMode, minDb, maxDb, minFreq, maxFreq, fftSize, isDetectorEnabled, emergenceThresholdDb, magnitudeGateDbFS, trackedHarmonicTags, activeFilters, kinematicsConfig, isDrawingMode, currentUserPoints, manualTrackedOrders)
            }
            Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                HudPanel(viewModel, currentUserPoints, manualTrackedOrders, selectedValidatedOrder, localContext)
            }
        }
    }
}

@Composable
fun SpectrogramArea(
    viewModel: MainViewModel,
    fftHistory: List<FloatArray>,
    fftHistoryAbsolute: List<FloatArray>,
    fftHistoryTTNR: List<FloatArray>,
    displayMode: DisplayMode,
    minDb: Float,
    maxDb: Float,
    minFreq: Float,
    maxFreq: Float,
    fftSize: Int,
    isDetectorEnabled: Boolean,
    emergenceThresholdDb: Float,
    magnitudeGateDbFS: Float,
    trackedHarmonicTags: List<HarmonicTag>,
    activeFilters: List<AudioFilterConfig>,
    kinematicsConfig: KinematicsConfig,
    isDrawingMode: Boolean,
    currentUserPoints: List<PointF>,
    manualTrackedOrders: List<TrackedOrder>
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
            telemetryHistory = emptyList(),
            isReportModeActive = true,
            isDrawingMode = isDrawingMode,
            currentUserPoints = currentUserPoints,
            manualTrackedOrders = manualTrackedOrders,
            onAddManualPoint = { frame, bin ->
                viewModel.addManualTrackPoint(frame, bin)
            }
        )

        // Overlay HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Selectors
            Row(
                modifier = Modifier
                    .background(Color(0x801A1A2E), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (displayMode == DisplayMode.ABSOLUTE) Color(0xFF00E5FF) else Color.Transparent,
                    modifier = Modifier.clickable { viewModel.setDisplayMode(DisplayMode.ABSOLUTE) }
                ) {
                    Text("Absolue", color = if (displayMode == DisplayMode.ABSOLUTE) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (displayMode == DisplayMode.TTNR) Color(0xFF00E5FF) else Color.Transparent,
                    modifier = Modifier.clickable { viewModel.setDisplayMode(DisplayMode.TTNR) }
                ) {
                    Text("TTNR", color = if (displayMode == DisplayMode.TTNR) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Draw/Nav Toggle
            Row(
                modifier = Modifier
                    .background(Color(0xAA121212), RoundedCornerShape(20.dp))
                    .border(2.dp, if (isDrawingMode) Color(0xFFFFC107) else Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (!isDrawingMode) Color(0xFF00E5FF) else Color.Transparent,
                    modifier = Modifier.clickable { if(isDrawingMode) viewModel.toggleDrawingMode() }
                ) {
                    Text("Navigation", color = if (!isDrawingMode) Color.Black else Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDrawingMode) Color(0xFFFFC107) else Color.Transparent,
                    modifier = Modifier.clickable { if(!isDrawingMode) viewModel.toggleDrawingMode() }
                ) {
                    Text("Dessin", color = if (isDrawingMode) Color.Black else Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (kinematicsConfig.isEnabled) {
                Column(
                    modifier = Modifier
                        .background(Color(0x80283593), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x609FA8DA), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("GMPe Active", color = Color(0xFFC5CAE9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val vName = kinematicsConfig.vehicleName
                    val mName = kinematicsConfig.motorName
                    val v1000 = String.format(java.util.Locale.US, "%.1f", kinematicsConfig.getEffectiveV1000())
                    Text(" |  | V1000=", color = Color(0xFFE8EAF6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.width(100.dp))
            }
        }
    }
}

@Composable
fun HudPanel(
    viewModel: MainViewModel,
    currentUserPoints: List<PointF>,
    manualTrackedOrders: List<TrackedOrder>,
    selectedValidatedOrder: TrackedOrder?,
    localContext: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xD91E1E2E), Color(0xCC12121A))
                )
            )
            .border(1.dp, Color(0x30FFFFFF))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("RAPPORT MANUEL", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            HorizontalDivider(color = Color(0x40FFFFFF), thickness = 1.dp)
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x40000000), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (manualTrackedOrders.isEmpty()) {
                    Text("Aucun ordre valide.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(manualTrackedOrders) { order ->
                            val isSelected = (order == selectedValidatedOrder)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectValidatedOrder(order) },
                                color = if (isSelected) Color(0x60F44336) else Color(0x40FFFFFF),
                                shape = RoundedCornerShape(6.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(12.dp).background(order.color, RoundedCornerShape(6.dp)))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(order.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            val pts = currentUserPoints.size
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (pts >= 2) Color(0x404CAF50) else Color(0x20FFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (pts >= 2) Color(0xFF4CAF50) else Color(0x40FFFFFF))
            ) {
                Text(
                    text = if (pts > 0) " points traces" else "Tracez des points...",
                    color = if (pts >= 2) Color(0xFF69F0AE) else Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.clearCurrentPoints() },
                    enabled = pts > 0,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x80FF9800), disabledContainerColor = Color(0x40FF9800))
                ) {
                    Text("Effacer Traces", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.validateCurrentOrder() },
                    enabled = pts >= 2,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), disabledContainerColor = Color(0x4000E676))
                ) {
                    Text("VALIDER ORDRE", color = if (pts >= 2) Color.Black else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { selectedValidatedOrder?.let { viewModel.removeValidatedOrder(it) } },
                enabled = selectedValidatedOrder != null,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x80F44336), disabledContainerColor = Color(0x40F44336))
            ) {
                Text("Supprimer Selection", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.generatePdfReport(localContext) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                modifier = Modifier.height(56.dp).fillMaxWidth()
            ) {
                Text("EXPORTER LE PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }

            Button(
                onClick = { viewModel.toggleReportMode() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF5252)),
                modifier = Modifier.height(56.dp).fillMaxWidth()
            ) {
                Text("QUITTER", color = Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}
'''

with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(kotlin_code)
print("File completely rewritten.")
