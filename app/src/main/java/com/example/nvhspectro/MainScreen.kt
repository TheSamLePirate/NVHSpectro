package com.example.nvhspectro

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Popup
import com.example.nvhspectro.ui.InfoDialog
import com.example.nvhspectro.ui.OrderSelectionDialog
import com.example.nvhspectro.ui.SplashScreen
import com.example.nvhspectro.ui.TelemetryGraph
import com.example.nvhspectro.ui.TelemetryMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var showSplash by remember { mutableStateOf(true) }
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
    } else if (permissionsGranted) {
        val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        AppScreen(viewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("En attente des permissions (Microphone, GPS)...")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: MainViewModel) {
    val telemetry by viewModel.telemetryState.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()

    val fftHistory by viewModel.fftHistory.collectAsState()
    val fftHistoryAbsolute by viewModel.fftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by viewModel.fftHistoryTTNR.collectAsState()
    val isDetectorEnabled by viewModel.isDetectorEnabled.collectAsState()
    val emergenceThresholdDb by viewModel.emergenceThresholdDb.collectAsState()
    val magnitudeGateDbFS by viewModel.magnitudeGateDbFS.collectAsState()
    val latestTTNRSpectrum by viewModel.latestTTNRSpectrum.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    
    val minDb by viewModel.minDb.collectAsState()
    val maxDb by viewModel.maxDb.collectAsState()
    val fftSize by viewModel.fftSize.collectAsState()
    val minFreq by viewModel.minFreq.collectAsState()
    val maxFreq by viewModel.maxFreq.collectAsState()
    val timeWindowSec by viewModel.timeWindowSec.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val isFrozen by viewModel.isFrozen.collectAsState()

    val kinematicsConfig by viewModel.kinematicsConfig.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val trackedHarmonicTags by viewModel.trackedHarmonicTags.collectAsState()
    val emergenceReportEntries by viewModel.emergenceReportEntries.collectAsState()
    
    val isAudioRecording by viewModel.isAudioRecording.collectAsState()
    val recordingElapsedSec by viewModel.recordingElapsedSec.collectAsState()
    val showSaveRecordingDialog by viewModel.showSaveRecordingDialog.collectAsState()

    val audioSourceMode by viewModel.audioSourceMode.collectAsState()
    val showAudioModeMenu by viewModel.showAudioModeMenu.collectAsState()
    val showWavSelectionDialog by viewModel.showWavSelectionDialog.collectAsState()
    val loadedWavData by viewModel.loadedWavData.collectAsState()
    val analysisNotice by viewModel.analysisNotice.collectAsState()
    val loadedWavFileName by viewModel.loadedWavFileName.collectAsState()
    val wavPlaybackPositionMs by viewModel.wavPlaybackPositionMs.collectAsState()
    val isWavPlaying by viewModel.isWavPlaying.collectAsState()
    val isReportModeActive by viewModel.isReportModeActive.collectAsState()
    val currentSmartPath by viewModel.currentSmartPath.collectAsState()
    val currentUserPoints by viewModel.currentUserPoints.collectAsState()
    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsState()

    val showVideoSelectionDialog by viewModel.showVideoSelectionDialog.collectAsState()
    val loadedVideoUri by viewModel.loadedVideoUri.collectAsState()
    val loadedYouTubeUrl by viewModel.loadedYouTubeUrl.collectAsState()
    val loadedVideoTitle by viewModel.loadedVideoTitle.collectAsState()
    val processingEstimateMessage by viewModel.processingEstimateMessage.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val wavPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadWavFromUri(context, uri)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadVideoFromUri(context, uri)
        }
    }

    val isWavMode = (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER || audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO)
    val isVideoMode = (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO)
    // [C1] The rate every frequency axis/order computation must use: the loaded
    // file's own rate in analyzer/video mode, the live capture rate otherwise.
    val analysisSampleRate = if (isWavMode) (loadedWavData?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ) else AudioConfig.LIVE_SAMPLE_RATE_HZ
    val wavProgress = if (loadedWavData != null && (loadedWavData?.durationMs ?: 0L) > 0) (wavPlaybackPositionMs.toFloat() / loadedWavData!!.durationMs.toFloat()) else 0f

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showKinematicsDialog by remember { mutableStateOf(false) }
    var showEmergenceReportDialog by remember { mutableStateOf(false) }
    var showOrderSelectionDialog by remember { mutableStateOf(false) }
    var showProjectedOrderDialog by remember { mutableStateOf(false) }

    if (isReportModeActive) {
        com.example.nvhspectro.ui.ReportModeScreen(viewModel = viewModel, onBack = { viewModel.toggleReportMode() })
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NVH Spectro", fontWeight = FontWeight.Bold) },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_vibratec),
                        contentDescription = "Logo Vibratec",
                        modifier = Modifier
                            .height(28.dp)
                            .padding(end = 6.dp),
                        contentScale = ContentScale.Fit
                    )
                    TextButton(onClick = { showInfoDialog = true }) {
                        Text("Informations", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = { showSettingsDialog = true }) {
                        Text("Réglages", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Bouton Analyse GMPe
                    Button(
                        onClick = { showKinematicsDialog = true },
                        enabled = !isVideoMode,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVideoMode) Color.DarkGray else if (kinematicsConfig.isEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color(0xFF424242),
                            disabledContentColor = Color.Gray,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (kinematicsConfig.isEnabled) "⚙️ GMPe (Actif)" else "⚙️ GMPe",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // 2. Bouton Rapport d'Emergence
                    Button(
                        onClick = { viewModel.toggleReportMode() },
                        enabled = !isVideoMode,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReportModeActive) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color(0xFF424242),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        Text(
                            text = if (isReportModeActive) "Quitter Rapport" else "Rapport Manuel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // 3. Bouton Figer / Dégeler (avec sous-option Exporter au-dessus si Figer est actif)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (isFrozen) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = androidx.compose.ui.unit.IntOffset(0, -100)
                            ) {
                                Button(
                                    onClick = { showExportDialog = true },
                                    modifier = Modifier
                                        .width(105.dp)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0275D8),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "📸 Exporter",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleFreeze() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFrozen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = if (isFrozen) "▶ Dégeler" else "⏸ Figer",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // 4. Bouton Audio (avec sous-options En direct, WAV et Vidéo empilées vers le haut)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (showAudioModeMenu) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = androidx.compose.ui.unit.IntOffset(0, -250),
                                onDismissRequest = { viewModel.toggleAudioModeMenu() }
                            ) {
                                Column(
                                    modifier = Modifier.width(115.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.LIVE)
                                            viewModel.toggleAudioModeMenu()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.LIVE) Color(0xFF15803D) else Color(0xFF334155),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "🔴 En direct",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER)
                                            viewModel.toggleAudioModeMenu()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER) Color(0xFFD97706) else Color(0xFF334155),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "📁 Analyseur WAV",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.VIDEO)
                                            viewModel.toggleAudioModeMenu()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO) Color(0xFF1E88E5) else Color(0xFF334155),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "🎬 Vidéo",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleAudioModeMenu() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (audioSourceMode) {
                                    com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER -> Color(0xFFD97706)
                                    com.example.nvhspectro.AudioSourceMode.VIDEO -> Color(0xFF1E88E5)
                                    else -> Color(0xFF673AB7)
                                },
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = when (audioSourceMode) {
                                    com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER -> "📁 Audio (WAV)"
                                    com.example.nvhspectro.AudioSourceMode.VIDEO -> "🎬 Audio (Vidéo)"
                                    else -> "🎙️ En direct"
                                },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Zone 1: Spectrogramme (55% hauteur)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SpectrogramCanvas(
                    history = fftHistory,
                    absHistory = fftHistoryAbsolute,
                    ttnrHistory = fftHistoryTTNR,
                    minDb = minDb,
                    maxDb = maxDb,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    fftSize = if (isWavMode) AudioConfig.WAV_FFT_SIZE else fftSize,
                    sampleRate = analysisSampleRate,
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
                    telemetryHistory = viewModel.telemetryHistory.collectAsState().value
                )

                // Superposition d'éléments en haut à gauche (Sélecteur de Mode + Bannière Cinématique GMPe en dessous)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Sélecteur de Mode (Absolue vs TTNR) + Bouton Enregistrement
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DisplayMode.values().forEach { mode ->
                            FilterChip(
                                selected = (displayMode == mode),
                                onClick = { viewModel.setDisplayMode(mode) },
                                label = { Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = Color(0xDD1E2430),
                                    labelColor = Color(0xFFE0E0E0)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = (displayMode == mode),
                                    borderColor = Color(0x66FFFFFF),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Bouton Enregistrement (Live) OU Bouton Charger WAV (Analyseur WAV) OU Charger Vidéo (Vidéo)
                        if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO) {
                            FilterChip(
                                selected = (loadedVideoUri != null || !loadedYouTubeUrl.isNullOrBlank()),
                                onClick = { viewModel.openVideoSelectionDialog() },
                                label = {
                                    Text(
                                        text = if (loadedVideoTitle.isNotBlank()) "🎬 ${loadedVideoTitle.take(14)}" else "🎬 Charger Vidéo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1E88E5),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xDD1E2430),
                                    labelColor = Color(0xFFE0E0E0)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = (loadedVideoUri != null || !loadedYouTubeUrl.isNullOrBlank()),
                                    borderColor = Color(0x66FFFFFF),
                                    selectedBorderColor = Color(0xFF42A5F5)
                                )
                            )
                        } else if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER) {
                            FilterChip(
                                selected = (loadedWavFileName != null),
                                onClick = { viewModel.openWavSelectionDialog() },
                                label = {
                                    Text(
                                        text = if (loadedWavFileName != null) "📂 ${loadedWavFileName!!.take(14)}" else "📂 Charger WAV",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD97706),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xDD1E2430),
                                    labelColor = Color(0xFFE0E0E0)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = (loadedWavFileName != null),
                                    borderColor = Color(0x66FFFFFF),
                                    selectedBorderColor = Color(0xFFF59E0B)
                                )
                            )
                        } else {
                            FilterChip(
                                selected = isAudioRecording,
                                onClick = { viewModel.toggleAudioRecording() },
                                label = {
                                    if (isAudioRecording) {
                                        val secStr = String.format("%02d:%02d", recordingElapsedSec / 60, recordingElapsedSec % 60)
                                        Text("🔴 STOP ($secStr / 00:30)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    } else {
                                        Text("🎙️ Enregistrement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0))
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD32F2F),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xDD1E2430),
                                    labelColor = Color(0xFFE0E0E0)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAudioRecording,
                                    borderColor = Color(0x66FFFFFF),
                                    selectedBorderColor = Color(0xFFEF5350)
                                )
                            )
                        }
                    }

                    // Indication dynamique Min & Max (Police ultra-compacte et discrète)
                    val rangeText = if (displayMode == DisplayMode.TTNR) {
                        "Dynamique : Min 0 | Max +20 dB (TTNR)"
                    } else {
                        "Dynamique : Min ${minDb.toInt()} | Max ${maxDb.toInt()} dBFS"
                    }
                    Surface(
                        color = Color(0xAA121212),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = rangeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCFD8DC),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Bannière Cinématique GMPe & Bannière Harmoniques Cibles
                    if (kinematicsConfig.isEnabled) {
                        val effV1000 = kinematicsConfig.getEffectiveV1000()
                        val curSpeed = telemetry.speedKmh
                        val isActiveSpeed = curSpeed > 1.0f

                        // 1. Bannière initiale d'état GMPe
                        Surface(
                            color = Color(0xCC121212),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // LED d'état (10dp) : ROUGE si Vitesse <= 1 km/h, VERTE si > 1 km/h
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (isActiveSpeed) Color(0xFF00E676) else Color(0xFFFF1744),
                                            CircleShape
                                        )
                                )
                                val titleText = if (kinematicsConfig.vehicleName.isNotEmpty()) kinematicsConfig.vehicleName else "GMPe"
                                
                                if (isActiveSpeed) {
                                    val h1Hz = kinematicsConfig.calculateH1FreqHz(curSpeed)
                                    val curRpm = kinematicsConfig.calculateRpm(curSpeed).toInt()
                                    Text(
                                        text = "🚘 $titleText | V1000: %.1f km/h | H1: %.1fHz (%d RPM)".format(effV1000, h1Hz, curRpm),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "🚘 $titleText | Inactif (< 1 km/h) | V1000: %.1f km/h".format(effV1000),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFCDD2)
                                    )
                                }
                            }
                        }

                        // 2. Bannière additionnelle des Harmoniques Cibles (si renseignées)
                        val targetOrdersList = kinematicsConfig.parsedTargetOrders()
                        if (targetOrdersList.isNotEmpty()) {
                            val targetStr = targetOrdersList.joinToString(", ") { if (it % 1.0 == 0.0) "H${it.toInt()}" else "H%.1f".format(it) }

                            Surface(
                                color = Color(0xF00F172A),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF00E5FF), CircleShape)
                                    )
                                    Text(
                                        text = "🎯 FILTRE CIBLES : $targetStr",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00E5FF),
                                        letterSpacing = 0.4.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bandeau analyse [C2/C3] : rejets de fichiers, troncature. Tap = fermer.
                    analysisNotice?.let { notice ->
                        Surface(
                            color = Color(0xE6301B0F),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.clickable { viewModel.dismissAnalysisNotice() }
                        ) {
                            Text(
                                text = "$notice   ✕",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFE0B2),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!processingEstimateMessage.isNullOrEmpty()) {
                    Surface(
                        color = Color(0xF00F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
                            Text(
                                text = processingEstimateMessage!!,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER && loadedWavFileName == null) {
                    Surface(
                        color = Color(0xEE121212),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Pas de données WAV chargées\nCliquez sur '📂 Charger WAV' pour ouvrir un fichier (limité à 5 min max).",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (fftHistory.isEmpty()) {
                    Text("Analyse audio & sonogramme en cours...", color = Color.White)
                }
            }





            if (!isReportModeActive) {
                // Lecteur WAV (si un fichier est chargé en mode Analyseur WAV)
                if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER && loadedWavData != null) {
                    com.example.nvhspectro.ui.WavPlayerBar(
                        fileName = loadedWavFileName ?: "fichier.wav",
                        currentPosMs = wavPlaybackPositionMs,
                        totalDurationMs = loadedWavData?.durationMs ?: 0L,
                        isPlaying = isWavPlaying,
                        onPlayToggle = { viewModel.toggleWavPlayPause() },
                        onSeekTo = { pos -> viewModel.seekWavTo(pos) },
                        onStepSeconds = { sec -> viewModel.stepWavSeconds(sec) }
                    )
                }

                // Zone 2: Données Véhicule / Télémétrie OU Lecteur Vidéo (Mode Vidéo)
                if (isVideoMode) {
                    com.example.nvhspectro.ui.VideoPlayerView(
                        videoUri = loadedVideoUri,
                        youtubeUrl = loadedYouTubeUrl,
                        videoTitle = loadedVideoTitle,
                        isPlaying = isWavPlaying,
                        positionMs = wavPlaybackPositionMs,
                        durationMs = loadedWavData?.durationMs ?: 0L,
                        onSeekTo = { pos -> viewModel.seekWavTo(pos) },
                        onTogglePlayPause = { viewModel.toggleWavPlayPause() },
                        onOpenVideoSelection = { viewModel.openVideoSelectionDialog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                            .padding(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // En-tête : Titre + Bouton H1 + LED Signal GPS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DONNÉES GPS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                // Bouton 👁️ Hx intégré à l'en-tête (Si GMPe activé)
                                if (kinematicsConfig.isEnabled) {
                                    val showH1Overlay by viewModel.showH1Overlay.collectAsState()
                                    val projectedOrder by viewModel.projectedOrder.collectAsState()
                                    val ordLabel = if (projectedOrder % 1.0 == 0.0) "H${projectedOrder.toInt()}" else "H%.1f".format(projectedOrder)
                                
                                    FilterChip(
                                        selected = showH1Overlay,
                                        onClick = { viewModel.toggleH1Overlay() },
                                        label = { Text("👁️ $ordLabel", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        trailingIcon = {
                                            Text(
                                                text = "⚙️",
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .clickable { showProjectedOrderDialog = true }
                                            )
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0x3300E5FF),
                                            selectedLabelColor = Color(0xFF00E5FF),
                                            containerColor = Color.Transparent,
                                            labelColor = Color.Gray
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = showH1Overlay,
                                            borderColor = Color.DarkGray,
                                            selectedBorderColor = Color(0xFF00E5FF)
                                        ),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }

                                // LED GPS
                                GpsLedIndicator(status = telemetry.gpsStatus)
                            }

                            // Encart des valeurs instantanées (Vitesse, Accélération, Ordre Traqué)
                            val ordVal = kinematicsConfig.selectedTrackedOrder
                            val ordLabel = if (ordVal % 1.0 == 0.0) "H${ordVal.toInt()}" else "H%.1f".format(ordVal)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val isGMPe = kinematicsConfig.isEnabled
                                if (isGMPe) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Vitesse", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                        Text(text = "GPS: %.1f".format(telemetry.speedKmh), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "Théo: %.1f".format(telemetry.theoreticalSpeedKmh), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0275D8))
                                    }
                                } else {
                                    KpiItem("Vitesse", String.format("%.1f km/h", telemetry.speedKmh))
                                }
                                KpiItem("Accélération", String.format("%.2f g", telemetry.accelerationG))
                                var throttledOrderDbFS by remember { mutableDoubleStateOf(-120.0) }
                                var lastOrderUpdateTime by remember { mutableLongStateOf(0L) }
                            
                                val currentMillis = System.currentTimeMillis()
                                if (currentMillis - lastOrderUpdateTime > 500 || kotlin.math.abs(throttledOrderDbFS - telemetry.trackedOrderDbFS) > 30.0) {
                                    throttledOrderDbFS = telemetry.trackedOrderDbFS
                                    lastOrderUpdateTime = currentMillis
                                }

                                KpiItem(
                                    "Ordre $ordLabel",
                                    when {
                                        !kinematicsConfig.isEnabled -> "Inactif"
                                        telemetry.speedKmh <= 1.0f -> "/"
                                        else -> String.format("%.1f dBFS", throttledOrderDbFS)
                                    }
                                )
                            }

                            Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                            // Onglets Sélecteurs de métrique pour le graphique 2D
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TelemetryMetric.values().forEach { metric ->
                                    val isOrderMetric = (metric == TelemetryMetric.ORDER)
                                    val isSelected = (selectedMetric == metric)
                                    val chipText = if (isOrderMetric && kinematicsConfig.isEnabled) {
                                        "Ordre ($ordLabel) ⚙️"
                                    } else {
                                        metric.label
                                    }

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isOrderMetric && isSelected && kinematicsConfig.isEnabled) {
                                                showOrderSelectionDialog = true
                                            } else {
                                                viewModel.selectMetric(metric)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chipText,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }

                            // Zone Graphique 2D synchronisé 1-to-1
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.Black.copy(alpha = 0.7f))
                            ) {
                                TelemetryGraph(
                                    history = telemetryHistory,
                                    metric = selectedMetric,
                                    timeWindowSec = if (isWavMode && loadedWavData != null) ((loadedWavData?.durationMs ?: 0L) / 1000.0) else timeWindowSec,
                                    historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else viewModel.historySize,
                                    ttnrSpectrum = latestTTNRSpectrum,
                                    minFreq = minFreq,
                                    maxFreq = maxFreq,
                                    sampleRate = analysisSampleRate,
                                    isKinematicsEnabled = kinematicsConfig.isEnabled,
                                    selectedOrderName = ordLabel,
                                    isWavAnalyzerMode = isWavMode,
                                    wavPlaybackProgress = wavProgress
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showVideoSelectionDialog) {
            com.example.nvhspectro.ui.VideoSelectionDialog(
                onDismiss = { viewModel.dismissVideoSelectionDialog() },
                onSelectLocalVideo = { videoPickerLauncher.launch("video/*") },
                onSelectYouTubeUrl = { url -> viewModel.loadVideoFromYouTube(url) }
            )
        }
        
        if (showInfoDialog) {
            InfoDialog(
                onDismiss = { showInfoDialog = false }
            )
        }

        if (showOrderSelectionDialog) {
            OrderSelectionDialog(
                currentOrder = kinematicsConfig.selectedTrackedOrder,
                onOrderSelected = { newOrd ->
                    viewModel.updateSelectedTrackedOrder(newOrd)
                },
                onDismiss = { showOrderSelectionDialog = false }
            )
        }

        if (showProjectedOrderDialog) {
            val projectedOrder by viewModel.projectedOrder.collectAsState()
            OrderSelectionDialog(
                currentOrder = projectedOrder,
                onOrderSelected = { newOrd ->
                    viewModel.setProjectedOrder(newOrd)
                },
                onDismiss = { showProjectedOrderDialog = false }
            )
        }

        if (showSettingsDialog) {
            com.example.nvhspectro.ui.SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                sampleRateHz = analysisSampleRate,
                activeFilters = activeFilters,
                onAddFilter = { filter -> viewModel.addAudioFilter(filter) },
                onRemoveFilter = { filterId -> viewModel.removeAudioFilter(filterId) },
                minDb = minDb,
                maxDb = maxDb,
                onMinDbChange = { viewModel.updateSettings(it, maxDb, fftSize, minFreq, maxFreq, timeWindowSec) },
                onMaxDbChange = { viewModel.updateSettings(minDb, it, fftSize, minFreq, maxFreq, timeWindowSec) },
                fftSize = fftSize,
                onFftSizeChange = { viewModel.updateSettings(minDb, maxDb, it, minFreq, maxFreq, timeWindowSec) },
                minFreq = minFreq,
                onMinFreqChange = { viewModel.updateSettings(minDb, maxDb, fftSize, it, maxFreq, timeWindowSec) },
                maxFreq = maxFreq,
                onMaxFreqChange = { viewModel.updateSettings(minDb, maxDb, fftSize, minFreq, it, timeWindowSec) },
                timeWindowSec = timeWindowSec,
                onTimeWindowChange = { viewModel.updateSettings(minDb, maxDb, fftSize, minFreq, maxFreq, it) },
                isDetectorEnabled = isDetectorEnabled,
                onDetectorEnabledChange = { enabled ->
                    viewModel.updateDetectorSettings(enabled, emergenceThresholdDb, magnitudeGateDbFS)
                },
                emergenceThresholdDb = emergenceThresholdDb,
                onEmergenceThresholdChange = { threshold ->
                    viewModel.updateDetectorSettings(isDetectorEnabled, threshold, magnitudeGateDbFS)
                },
                magnitudeGateDbFS = magnitudeGateDbFS,
                onMagnitudeGateChange = { gate ->
                    viewModel.updateDetectorSettings(isDetectorEnabled, emergenceThresholdDb, gate)
                },
                isWavAnalyzerMode = isWavMode,
                wavDurationSec = (loadedWavData?.durationMs ?: 0L) / 1000.0
            )
        }
        
        if (showExportDialog) {
            com.example.nvhspectro.ui.ExportDialog(
                onDismiss = { showExportDialog = false },
                telemetry = telemetry,
                onExport = { pedalPercent, comments ->
                    showExportDialog = false
                    viewModel.exportData(pedalPercent, comments)
                }
            )
        }

        if (showKinematicsDialog) {
            com.example.nvhspectro.ui.KinematicsDialog(
                currentConfig = kinematicsConfig,
                onDismiss = { showKinematicsDialog = false },
                onSave = { newConfig ->
                    showKinematicsDialog = false
                    viewModel.updateKinematicsConfig(newConfig)
                }
            )
        }

        if (showEmergenceReportDialog) {
            com.example.nvhspectro.ui.EmergenceReportDialog(
                entries = emergenceReportEntries,
                kinematicsConfig = kinematicsConfig,
                onDismiss = { showEmergenceReportDialog = false },
                onClearReport = { viewModel.clearEmergenceReport() }
            )
        }

        if (showSaveRecordingDialog) {
            com.example.nvhspectro.ui.SaveRecordingDialog(
                durationSec = recordingElapsedSec,
                onSave = { customName ->
                    viewModel.saveAudioRecording(customName)
                },
                onDismiss = {
                    viewModel.cancelSaveAudioRecording()
                }
            )
        }

        if (showWavSelectionDialog) {
            com.example.nvhspectro.ui.WavSelectionDialog(
                onDismiss = { viewModel.closeWavSelectionDialog() },
                onSelectEntry = { wavFile, jsonFile ->
                    viewModel.loadWavFile(wavFile, jsonFile)
                },
                onImportExternal = {
                    viewModel.closeWavSelectionDialog()
                    wavPickerLauncher.launch("audio/*")
                }
            )
        }
    }
    }
}

@Composable
fun GpsLedIndicator(status: GpsStatus) {
    val (ledColor, textLabel) = when (status) {
        GpsStatus.GOOD -> Color(0xFF00E676) to "Signal OK"
        GpsStatus.POOR -> Color(0xFFFF9100) to "Signal Médiocre"
        GpsStatus.NONE -> Color(0xFFFF5252) to "Signal Perdu"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Signal GPS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = ledColor, shape = CircleShape)
        )
        Text(text = textLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun KpiItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

