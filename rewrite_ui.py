import re

# We will completely rewrite ReportModeScreen to match the Landscape schema precisely
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

new_content = '''package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.MainViewModel
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.ManualOrderAnchor
import com.example.nvhspectro.data.SmartTrackedOrder
import com.example.nvhspectro.data.TrackedHarmonicTag
import com.example.nvhspectro.SpectrogramColormap
import com.example.nvhspectro.DisplayMode

@Composable
fun ReportModeScreen(viewModel: MainViewModel) {
    val isReportModeActive by viewModel.isReportModeActive.collectAsState()
    val isDrawingMode by viewModel.isDrawingMode.collectAsState()
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()
    val selectedValidatedOrder by viewModel.selectedValidatedOrder.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()

    val fftHistory by viewModel.fftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.fftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.fftHistoryTTNR.collectAsState()
    
    val sampleRate by viewModel.sampleRate.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val isWavAnalyzerMode by viewModel.isWavAnalyzerMode.collectAsState()
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val trackedHarmonicTags by viewModel.trackedHarmonicTags.collectAsState()
    val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()
    
    val localContext = LocalContext.current

    if (isReportModeActive) {
        // Landscape schema priority layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT COLUMN (Toggles + Colormap + Bottom Actions)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggles Left
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Display Mode Toggle
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x30FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x50FFFFFF))
                        ) {
                            Row {
                                Box(
                                    modifier = Modifier
                                        .background(if (displayMode == DisplayMode.ABSOLUTE) Color(0xFF00E5FF) else Color.Transparent)
                                        .clickable { viewModel.setDisplayMode(DisplayMode.ABSOLUTE) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text("Absolue", color = if (displayMode == DisplayMode.ABSOLUTE) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (displayMode == DisplayMode.TTNR) Color(0xFF00E5FF) else Color.Transparent)
                                        .clickable { viewModel.setDisplayMode(DisplayMode.TTNR) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text("TTNR", color = if (displayMode == DisplayMode.TTNR) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Navigation/Dessin Toggle
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x30FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x50FFFFFF))
                        ) {
                            Row {
                                Box(
                                    modifier = Modifier
                                        .background(if (!isDrawingMode) Color(0xFF00E5FF) else Color.Transparent)
                                        .clickable { if(isDrawingMode) viewModel.toggleDrawingMode() }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text("Navigation", color = if (!isDrawingMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isDrawingMode) Color(0xFF00E5FF) else Color.Transparent)
                                        .clickable { if(!isDrawingMode) viewModel.toggleDrawingMode() }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text("Dessin", color = if (isDrawingMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // GMPe Info Right
                    if (kinematicsConfig.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x209C27B0),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x509C27B0))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                                Text("Etude GMPe active", color = Color(0xFFE1BEE7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("VH ;  ; V1000=", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // COLORMAP
                Box(modifier = Modifier.weight(1f).fillMaxWidth().border(2.dp, Color.White)) {
                    SpectrogramColormap(
                        history = if (displayMode == DisplayMode.TTNR) fftHistoryTTNR else fftHistoryAbsolute,
                        sampleRate = sampleRate,
                        minFreq = minFreq.toFloat(),
                        maxFreq = maxFreq.toFloat(),
                        minDb = minDb,
                        maxDb = maxDb,
                        displayMode = displayMode,
                        trackedHarmonicTags = trackedHarmonicTags,
                        kinematicsConfig = kinematicsConfig,
                        isWavAnalyzerMode = isWavAnalyzerMode,
                        isReportModeActive = isReportModeActive,
                        isDrawingMode = isDrawingMode,
                        currentUserPoints = currentUserPoints,
                        manualTrackedOrders = manualTrackedOrders,
                        onAddManualPoint = { frameIdx, binIdx ->
                            viewModel.addManualPoint(frameIdx, binIdx)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // BOTTOM BAR (Export & Quit)
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.generatePdfReport(localContext) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4)),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0288D1))
                    ) {
                        Text("Export PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }

                    Button(
                        onClick = { viewModel.toggleReportMode() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800))
                    ) {
                        Text("Quitte rapport", color = Color(0xFFFF9800), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }

            // RIGHT COLUMN (List and tools)
            Column(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LISTE ORDRE (Red Border)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(3.dp, Color.Red, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Liste ordre", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (manualTrackedOrders.isEmpty()) {
                            Text("Aucun ordre...", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(manualTrackedOrders.size) { index ->
                                    val order = manualTrackedOrders[index]
                                    val isSelected = (order == selectedValidatedOrder)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectValidatedOrder(order) },
                                        color = if (isSelected) Color(0x60F44336) else Color(0x20FFFFFF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(12.dp).background(order.color, RoundedCornerShape(6.dp)))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(order.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // BUTTONS
                Button(
                    onClick = { viewModel.validateCurrentOrder() },
                    enabled = currentUserPoints.size >= 2,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC6FF00), disabledContainerColor = Color(0x40C6FF00)),
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFAEEA00))
                ) {
                    Text("valider ordre", color = if (currentUserPoints.size >= 2) Color.Black else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Button(
                    onClick = { viewModel.clearCurrentPoints() },
                    enabled = currentUserPoints.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF03A9F4))
                ) {
                    Text("supprime points", color = Color(0xFF03A9F4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Button(
                    onClick = { 
                        if(selectedValidatedOrder != null) {
                            viewModel.removeValidatedOrder(selectedValidatedOrder!!) 
                        }
                    },
                    enabled = selectedValidatedOrder != null,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF9C27B0))
                ) {
                    Text("supprime ordre", color = Color(0xFF9C27B0), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
'''
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
