import os

code = '''package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.MainViewModel
import com.example.nvhspectro.SpectrogramCanvas

@Composable
fun ReportModeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val reportFftHistory by viewModel.reportFftHistory.collectAsState()
    val reportAbsHistory by viewModel.reportAbsHistory.collectAsState()
    val reportTtnrHistory by viewModel.reportTtnrHistory.collectAsState()

    val displayMode by viewModel.displayMode.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val sampleRate = 44100
    val context = LocalContext.current

    var isDrawingMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // --- TOP: SPECTROGRAM (55%) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .background(Color.Black)
        ) {
            SpectrogramCanvas(
                history = reportFftHistory,
                absHistory = reportAbsHistory,
                ttnrHistory = reportTtnrHistory,
                modifier = Modifier.fillMaxSize(),
                minDb = minDb,
                maxDb = maxDb,
                minFreq = minFreq,
                maxFreq = maxFreq,
                fftSize = 2048,
                sampleRate = sampleRate,
                displayMode = displayMode,
                isWavAnalyzerMode = false, // It's report mode
                isReportModeActive = true,
                isDrawingMode = isDrawingMode,
                currentUserPoints = viewModel.manualOrderAnchors.collectAsState().value,
                onAddManualPoint = { frameIdx: Int, binIdx: Int ->
                    viewModel.addManualTrackPoint(frameIdx, binIdx)
                }
            )
        }

        // --- BOTTOM: CONTROLS (45%) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Absolue / TTNR Segmented Control
                Row(
                    modifier = Modifier
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(20.dp))
                        .padding(4.dp)
                ) {
                    SegmentedButton(
                        text = "Absolue",
                        isSelected = displayMode == DisplayMode.ABSOLUTE,
                        onClick = { viewModel.setDisplayMode(DisplayMode.ABSOLUTE) }
                    )
                    SegmentedButton(
                        text = "TTNR",
                        isSelected = displayMode == DisplayMode.TTNR,
                        onClick = { viewModel.setDisplayMode(DisplayMode.TTNR) }
                    )
                }

                // Navigation / Dessin Segmented Control
                Row(
                    modifier = Modifier
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(20.dp))
                        .padding(4.dp)
                ) {
                    SegmentedButton(
                        text = "Navigation",
                        isSelected = !isDrawingMode,
                        onClick = { isDrawingMode = false }
                    )
                    SegmentedButton(
                        text = "Dessin",
                        isSelected = isDrawingMode,
                        onClick = { isDrawingMode = true }
                    )
                }
            }

            HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

            // Points list & Actions
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // List of points
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF252525), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val points = viewModel.manualOrderAnchors.collectAsState().value
                    if (points.isEmpty()) {
                        Text(
                            "Aucun point placé.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(points) { index, pt ->
                                Text(
                                    text = "Pt \: \ Hz @ \s",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Actions (Effacer/Valider)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Button(
                        onClick = { viewModel.clearManualTrackPoints() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Effacer", tint = Color.White)
                    }
                    Button(
                        onClick = { viewModel.commitManualTrackToOrder() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Valider", tint = Color.White)
                    }
                }
            }

            // Bottom Actions (Retour / Generate PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.toggleReportMode() // Exit report mode
                        onBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retour Live")
                }

                Button(
                    onClick = { viewModel.generatePdfReport(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Générer PDF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SegmentedButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.LightGray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
'''
with open('app/src/main/java/com/example/nvhspectro/ui/ReportModeScreen.kt', 'w', encoding='utf-8') as f:
    f.write(code)
