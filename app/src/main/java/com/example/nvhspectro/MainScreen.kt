package com.example.nvhspectro

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
import com.example.nvhspectro.data.EstimateValidity
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhActiveContainer
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhCanvasChipBorder
import com.example.nvhspectro.theme.NvhCanvasPanel
import com.example.nvhspectro.theme.NvhCanvasScrim
import com.example.nvhspectro.theme.NvhDisabledContainer
import com.example.nvhspectro.theme.NvhDisabledContent
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhInactiveContainer
import com.example.nvhspectro.theme.NvhModeLive
import com.example.nvhspectro.theme.NvhModeVideo
import com.example.nvhspectro.theme.NvhModeVideoAccent
import com.example.nvhspectro.theme.NvhModeWav
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhNoticeBorder
import com.example.nvhspectro.theme.NvhNoticeContainer
import com.example.nvhspectro.theme.NvhOnNotice
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhRecording
import com.example.nvhspectro.theme.NvhReportMode
import com.example.nvhspectro.theme.NvhStatusBad
import com.example.nvhspectro.theme.NvhStatusGood
import com.example.nvhspectro.theme.NvhStatusWarn
import com.example.nvhspectro.theme.NvhTheoretical
import com.example.nvhspectro.ui.InfoDialog
import com.example.nvhspectro.ui.NvhPermissions
import com.example.nvhspectro.ui.OrderSelectionDialog
import com.example.nvhspectro.ui.PermissionGate
import com.example.nvhspectro.ui.openAppSettings
import com.example.nvhspectro.ui.SplashScreen
import com.example.nvhspectro.ui.TelemetryGraph
import com.example.nvhspectro.ui.TelemetryMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
        return
    }

    // [plan 3.3] Three session-sharing ViewModels replace the monolith.
    // LiveViewModel first: it registers its transition hooks before the others.
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val factory = remember { NvhViewModelFactory(app) }
    val liveVm: LiveViewModel =
        androidx.lifecycle.viewmodel.compose
            .viewModel(factory = factory)
    val analyzerVm: AnalyzerViewModel =
        androidx.lifecycle.viewmodel.compose
            .viewModel(factory = factory)
    val reportVm: ReportViewModel =
        androidx.lifecycle.viewmodel.compose
            .viewModel(factory = factory)

    // [U1, plan 4.1] Each permission degrades on its own; only "no microphone AND the user
    // has not chosen analyzer-only" blocks the UI, and even that offers a way through.
    PermissionGate(
        onMicrophoneUnavailable = { liveVm.session.setAudioSourceMode(AudioSourceMode.WAV_ANALYZER) },
    ) { permissions ->
        // Capture and GNSS must follow the *actual* grants, not the last-known ones: a
        // permission revoked from the system settings takes effect on the next resume.
        LaunchedEffect(permissions) { liveVm.onPermissionsChanged() }
        AppScreen(liveVm, analyzerVm, reportVm, permissions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    liveVm: LiveViewModel,
    analyzerVm: AnalyzerViewModel,
    reportVm: ReportViewModel,
    permissions: NvhPermissions,
) {
    val session = liveVm.session

    val telemetry by session.telemetryState.collectAsState()
    val telemetryHistory by session.telemetryHistory.collectAsState()
    val selectedMetric by liveVm.selectedMetric.collectAsState()

    val fftHistory by session.fftHistory.collectAsState()
    val fftHistoryAbsolute by session.fftHistoryAbsolute.collectAsState()
    val fftHistoryTTNR by session.fftHistoryTTNR.collectAsState()
    val isDetectorEnabled by session.isDetectorEnabled.collectAsState()
    val emergenceThresholdDb by session.emergenceThresholdDb.collectAsState()
    val magnitudeGateDbFS by session.magnitudeGateDbFS.collectAsState()
    val latestTTNRSpectrum by session.latestTTNRSpectrum.collectAsState()

    val minDb by session.minDb.collectAsState()
    val maxDb by session.maxDb.collectAsState()
    val fftSize by session.fftSize.collectAsState()
    val minFreq by session.minFreq.collectAsState()
    val maxFreq by session.maxFreq.collectAsState()
    val timeWindowSec by session.timeWindowSec.collectAsState()
    val displayMode by session.displayMode.collectAsState()
    val isFrozen by session.isFrozen.collectAsState()

    val kinematicsConfig by session.kinematicsConfig.collectAsState()
    val activeFilters by analyzerVm.activeFilters.collectAsState()
    val trackedHarmonicTags by session.trackedHarmonicTags.collectAsState()
    val emergenceReportEntries by session.emergenceReportEntries.collectAsState()

    val isAudioRecording by liveVm.isAudioRecording.collectAsState()
    val recordingElapsedSec by liveVm.recordingElapsedSec.collectAsState()
    val showSaveRecordingDialog by liveVm.showSaveRecordingDialog.collectAsState()

    val audioSourceMode by session.audioSourceMode.collectAsState()
    var showAudioModeMenu by remember { mutableStateOf(false) }
    var showWavSelectionDialog by remember { mutableStateOf(false) }
    val loadedWavData by session.loadedWavData.collectAsState()
    val analysisNotice by session.analysisNotice.collectAsState()
    val loadedWavFileName by analyzerVm.loadedWavFileName.collectAsState()
    val wavPlaybackPositionMs by analyzerVm.player.positionMs.collectAsState()
    val isWavPlaying by analyzerVm.player.isPlaying.collectAsState()
    val isReportModeActive by reportVm.isReportModeActive.collectAsState()

    val loadedVideoUri by analyzerVm.loadedVideoUri.collectAsState()
    val loadedVideoTitle by analyzerVm.loadedVideoTitle.collectAsState()
    val processingEstimateMessage by analyzerVm.processingEstimateMessage.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val wavPickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                analyzerVm.loadWavFromUri(context, uri)
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                analyzerVm.loadVideoFromUri(context, uri)
            }
        }

    val isWavMode = (
        audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER ||
            audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO
    )
    val isVideoMode = (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO)
    // [C1] The rate every frequency axis/order computation must use: the loaded
    // file's own rate in analyzer/video mode, the live capture rate otherwise.
    val analysisSampleRate = if (isWavMode) (loadedWavData?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ) else AudioConfig.LIVE_SAMPLE_RATE_HZ
    val wavProgress =
        if (loadedWavData != null &&
            (loadedWavData?.durationMs ?: 0L) > 0
        ) {
            (wavPlaybackPositionMs.toFloat() / loadedWavData!!.durationMs.toFloat())
        } else {
            0f
        }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showKinematicsDialog by remember { mutableStateOf(false) }
    var showEmergenceReportDialog by remember { mutableStateOf(false) }
    var showOrderSelectionDialog by remember { mutableStateOf(false) }
    var showProjectedOrderDialog by remember { mutableStateOf(false) }

    if (isReportModeActive) {
        com.example.nvhspectro.ui
            .ReportModeScreen(viewModel = reportVm, onBack = { reportVm.toggleReportMode() })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("NVH Spectro", fontWeight = FontWeight.Bold) },
                    actions = {
                        Image(
                            painter = painterResource(id = R.drawable.logo_vibratec),
                            contentDescription = "Logo Vibratec",
                            modifier =
                                Modifier
                                    .height(28.dp)
                                    .padding(end = 6.dp),
                            contentScale = ContentScale.Fit,
                        )
                        TextButton(onClick = { showInfoDialog = true }) {
                            Text("Informations", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        TextButton(onClick = { showSettingsDialog = true }) {
                            Text("Réglages", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                )
            },
            bottomBar = {
                BottomAppBar(
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Bouton Analyse GMPe
                        Button(
                            onClick = { showKinematicsDialog = true },
                            enabled = !isVideoMode,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = if (kinematicsConfig.isEnabled) NvhActiveContainer else MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = NvhDisabledContainer,
                                    disabledContentColor = NvhDisabledContent,
                                    contentColor = NvhOnSurface,
                                ),
                        ) {
                            Text(
                                text = if (kinematicsConfig.isEnabled) "⚙️ GMPe (Actif)" else "⚙️ GMPe",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }

                        // 2. Bouton Rapport d'Emergence
                        Button(
                            onClick = { reportVm.toggleReportMode() },
                            enabled = !isVideoMode,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = if (isReportModeActive) NvhReportMode else MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = NvhDisabledContainer,
                                    disabledContentColor = NvhDisabledContent,
                                    contentColor = NvhOnSurface,
                                ),
                        ) {
                            Text(
                                text = if (isReportModeActive) "Quitter Rapport" else "Rapport Manuel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }

                        // 3. Bouton Figer / Dégeler (avec sous-option Exporter au-dessus si Figer est actif)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (isFrozen) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    offset =
                                        androidx.compose.ui.unit
                                            .IntOffset(0, -100),
                                ) {
                                    Button(
                                        onClick = { showExportDialog = true },
                                        modifier =
                                            Modifier
                                                .width(105.dp)
                                                .height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = NvhExport,
                                                contentColor = NvhOnSurface,
                                            ),
                                    ) {
                                        Text(
                                            text = "📸 Exporter",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { session.toggleFreeze() },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = if (isFrozen) NvhRecording else MaterialTheme.colorScheme.secondary,
                                        contentColor = if (isFrozen) NvhOnSurface else MaterialTheme.colorScheme.onSecondary,
                                    ),
                            ) {
                                Text(
                                    text = if (isFrozen) "▶ Dégeler" else "⏸ Figer",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        // 4. Bouton Audio (avec sous-options En direct, WAV et Vidéo empilées vers le haut)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (showAudioModeMenu) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    offset =
                                        androidx.compose.ui.unit
                                            .IntOffset(0, -250),
                                    onDismissRequest = { showAudioModeMenu = false },
                                ) {
                                    Column(
                                        modifier = Modifier.width(115.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                if (permissions.liveCapture) {
                                                    session.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.LIVE)
                                                } else {
                                                    // [U1] Never a silently dead control: say why, and how to fix it.
                                                    session.postNotice(
                                                        "🎙️ Mesure en direct indisponible : autorisation micro refusée. " +
                                                            "Activez « Micro » dans les réglages Android.",
                                                    )
                                                }
                                                showAudioModeMenu = false
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        if (!permissions.liveCapture) {
                                                            NvhDisabledContainer
                                                        } else if (audioSourceMode ==
                                                            com.example.nvhspectro.AudioSourceMode.LIVE
                                                        ) {
                                                            NvhActiveContainer
                                                        } else {
                                                            NvhInactiveContainer
                                                        },
                                                    contentColor =
                                                        if (permissions.liveCapture) NvhOnSurface else NvhDisabledContent,
                                                ),
                                        ) {
                                            Text(
                                                text = if (permissions.liveCapture) "🔴 En direct" else "🚫 En direct",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                session.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER)
                                                showAudioModeMenu = false
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        if (audioSourceMode ==
                                                            com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER
                                                        ) {
                                                            NvhModeWav
                                                        } else {
                                                            NvhInactiveContainer
                                                        },
                                                    contentColor = NvhOnSurface,
                                                ),
                                        ) {
                                            Text(
                                                text = "📁 Analyseur WAV",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                session.setAudioSourceMode(com.example.nvhspectro.AudioSourceMode.VIDEO)
                                                showAudioModeMenu = false
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        if (audioSourceMode ==
                                                            com.example.nvhspectro.AudioSourceMode.VIDEO
                                                        ) {
                                                            NvhModeVideo
                                                        } else {
                                                            NvhInactiveContainer
                                                        },
                                                    contentColor = NvhOnSurface,
                                                ),
                                        ) {
                                            Text(
                                                text = "🎬 Vidéo",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { showAudioModeMenu = !showAudioModeMenu },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            when (audioSourceMode) {
                                                com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER -> NvhModeWav
                                                com.example.nvhspectro.AudioSourceMode.VIDEO -> NvhModeVideo
                                                else -> NvhModeLive
                                            },
                                        contentColor = NvhOnSurface,
                                    ),
                            ) {
                                Text(
                                    text =
                                        when (audioSourceMode) {
                                            com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER -> "📁 Audio (WAV)"
                                            com.example.nvhspectro.AudioSourceMode.VIDEO -> "🎬 Audio (Vidéo)"
                                            else -> "🎙️ En direct"
                                        },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // Zone 1: Spectrogramme (55% hauteur)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                            .background(NvhCanvas),
                    contentAlignment = Alignment.Center,
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
                        historySize = if (isWavMode && fftHistoryAbsolute.isNotEmpty()) fftHistoryAbsolute.size else session.historySize,
                        displayMode = displayMode,
                        isDetectorEnabled = isDetectorEnabled,
                        emergenceThresholdDb = emergenceThresholdDb,
                        magnitudeGateDbFS = magnitudeGateDbFS,
                        trackedHarmonicTags = trackedHarmonicTags,
                        activeFilters = activeFilters,
                        kinematicsConfig = kinematicsConfig,
                        isWavAnalyzerMode = isWavMode,
                        wavPlaybackProgress = wavProgress,
                        showH1Overlay = liveVm.showH1Overlay.collectAsState().value,
                        projectedOrder = liveVm.projectedOrder.collectAsState().value,
                        telemetryHistory = telemetryHistory,
                    )

                    // Superposition d'éléments en haut à gauche (Sélecteur de Mode + Bannière Cinématique GMPe en dessous)
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Sélecteur de Mode (Absolue vs TTNR) + Bouton Enregistrement
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DisplayMode.values().forEach { mode ->
                                FilterChip(
                                    selected = (displayMode == mode),
                                    onClick = { session.setDisplayMode(mode) },
                                    label = { Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = NvhCanvasScrim,
                                            labelColor = NvhOnSurface,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = (displayMode == mode),
                                            borderColor = NvhCanvasChipBorder,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }

                            // Bouton Enregistrement (Live) OU Bouton Charger WAV (Analyseur WAV) OU Charger Vidéo (Vidéo)
                            if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO) {
                                FilterChip(
                                    selected = (loadedVideoUri != null),
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    label = {
                                        Text(
                                            text =
                                                if (loadedVideoTitle.isNotBlank()) {
                                                    "🎬 ${loadedVideoTitle.take(
                                                        14,
                                                    )}"
                                                } else {
                                                    "🎬 Charger Vidéo"
                                                },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhOnSurface,
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NvhModeVideo,
                                            selectedLabelColor = NvhOnSurface,
                                            containerColor = NvhCanvasScrim,
                                            labelColor = NvhOnSurface,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = (loadedVideoUri != null),
                                            borderColor = NvhCanvasChipBorder,
                                            selectedBorderColor = NvhModeVideoAccent,
                                        ),
                                )
                            } else if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER) {
                                FilterChip(
                                    selected = (loadedWavFileName != null),
                                    onClick = { showWavSelectionDialog = true },
                                    label = {
                                        Text(
                                            text =
                                                if (loadedWavFileName !=
                                                    null
                                                ) {
                                                    "📂 ${loadedWavFileName!!.take(14)}"
                                                } else {
                                                    "📂 Charger WAV"
                                                },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhOnSurface,
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NvhModeWav,
                                            selectedLabelColor = NvhOnSurface,
                                            containerColor = NvhCanvasScrim,
                                            labelColor = NvhOnSurface,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = (loadedWavFileName != null),
                                            borderColor = NvhCanvasChipBorder,
                                            selectedBorderColor = NvhModeWavAccent,
                                        ),
                                )
                            } else {
                                FilterChip(
                                    selected = isAudioRecording,
                                    onClick = { liveVm.toggleAudioRecording() },
                                    label = {
                                        if (isAudioRecording) {
                                            val secStr = String.format("%02d:%02d", recordingElapsedSec / 60, recordingElapsedSec % 60)
                                            Text(
                                                "🔴 STOP ($secStr / 00:30)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhOnSurface,
                                            )
                                        } else {
                                            Text("🎙️ Enregistrement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NvhOnSurface)
                                        }
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NvhRecording,
                                            selectedLabelColor = NvhOnSurface,
                                            containerColor = NvhCanvasScrim,
                                            labelColor = NvhOnSurface,
                                        ),
                                    border =
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isAudioRecording,
                                            borderColor = NvhCanvasChipBorder,
                                            selectedBorderColor = NvhStatusBad,
                                        ),
                                )
                            }
                        }

                        // Indication dynamique Min & Max (Police ultra-compacte et discrète)
                        val rangeText =
                            if (displayMode == DisplayMode.TTNR) {
                                "Dynamique : Min 0 | Max +20 dB (TTNR)"
                            } else {
                                "Dynamique : Min ${minDb.toInt()} | Max ${maxDb.toInt()} dBFS"
                            }
                        Surface(
                            color = NvhCanvasScrim,
                            shape = RoundedCornerShape(3.dp),
                        ) {
                            Text(
                                text = rangeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NvhOnSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        // Bannière Cinématique GMPe & Bannière Harmoniques Cibles
                        if (kinematicsConfig.isEnabled) {
                            val effV1000 = kinematicsConfig.getEffectiveV1000()
                            val curSpeed = telemetry.speedKmh
                            val isActiveSpeed = curSpeed > 1.0f

                            // 1. Bannière initiale d'état GMPe
                            Surface(
                                color = NvhCanvasScrim,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    // LED d'état (10dp) : ROUGE si Vitesse <= 1 km/h, VERTE si > 1 km/h
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(10.dp)
                                                .background(
                                                    if (isActiveSpeed) NvhStatusGood else NvhStatusBad,
                                                    CircleShape,
                                                ),
                                    )
                                    val titleText = if (kinematicsConfig.vehicleName.isNotEmpty()) kinematicsConfig.vehicleName else "GMPe"

                                    if (isActiveSpeed) {
                                        val h1Hz = kinematicsConfig.calculateH1FreqHz(curSpeed)
                                        val curRpm = kinematicsConfig.calculateRpm(curSpeed).toInt()
                                        Text(
                                            text = "🚘 $titleText | V1000: %.1f km/h | H1: %.1fHz (%d RPM)".format(effV1000, h1Hz, curRpm),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhOnSurface,
                                        )
                                    } else {
                                        Text(
                                            text = "🚘 $titleText | Inactif (< 1 km/h) | V1000: %.1f km/h".format(effV1000),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhStatusWarn,
                                        )
                                    }
                                }
                            }

                            // 2. Bannière additionnelle des Harmoniques Cibles (si renseignées)
                            val targetOrdersList = kinematicsConfig.parsedTargetOrders()
                            if (targetOrdersList.isNotEmpty()) {
                                val targetStr =
                                    targetOrdersList.joinToString(", ") {
                                        if (it % 1.0 ==
                                            0.0
                                        ) {
                                            "H${it.toInt()}"
                                        } else {
                                            "H%.1f".format(it)
                                        }
                                    }

                                Surface(
                                    color = NvhCanvasPanel,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NvhAccent.copy(alpha = 0.8f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(8.dp)
                                                    .background(NvhAccent, CircleShape),
                                        )
                                        Text(
                                            text = "🎯 FILTRE CIBLES : $targetStr",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = NvhAccent,
                                            letterSpacing = 0.4.sp,
                                        )
                                    }
                                }
                            }
                        }

                        // Bandeau analyse [C2/C3] : rejets de fichiers, troncature. Tap = fermer.
                        analysisNotice?.let { notice ->
                            Surface(
                                color = NvhNoticeContainer,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NvhNoticeBorder),
                                modifier = Modifier.clickable { session.dismissNotice() },
                            ) {
                                Text(
                                    text = "$notice   ✕",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NvhOnNotice,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    if (!processingEstimateMessage.isNullOrEmpty()) {
                        Surface(
                            color = NvhCanvasPanel,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NvhAccent),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(color = NvhAccent, modifier = Modifier.size(32.dp))
                                Text(
                                    text = processingEstimateMessage!!,
                                    color = NvhOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER && loadedWavFileName == null) {
                        Surface(
                            color = NvhCanvasScrim,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = "Pas de données WAV chargées\nCliquez sur '📂 Charger WAV' pour ouvrir un fichier (limité à 5 min max).",
                                color = NvhOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else if (fftHistory.isEmpty()) {
                        Text("Analyse audio & sonogramme en cours...", color = NvhOnSurface)
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
                            onPlayToggle = { analyzerVm.player.togglePlayPause() },
                            onSeekTo = { pos -> analyzerVm.player.seekTo(pos) },
                            onStepSeconds = { sec -> analyzerVm.player.stepSeconds(sec) },
                        )
                    }

                    // Zone 2: Données Véhicule / Télémétrie OU Lecteur Vidéo (Mode Vidéo)
                    if (isVideoMode) {
                        com.example.nvhspectro.ui.VideoPlayerView(
                            videoUri = loadedVideoUri,
                            videoTitle = loadedVideoTitle,
                            state =
                                com.example.nvhspectro.ui.VideoPlaybackState(
                                    isPlaying = isWavPlaying,
                                    positionMs = wavPlaybackPositionMs,
                                    durationMs = loadedWavData?.durationMs ?: 0L,
                                ),
                            onSeekTo = { pos -> analyzerVm.player.seekTo(pos) },
                            onTogglePlayPause = { analyzerVm.player.togglePlayPause() },
                            onOpenVideoSelection = { videoPickerLauncher.launch("video/*") },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(0.45f),
                        )
                    } else {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(0.45f)
                                    .padding(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // En-tête : Titre + Bouton H1 + LED Signal GPS
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "DONNÉES GPS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )

                                    // Bouton 👁️ Hx intégré à l'en-tête (Si GMPe activé)
                                    if (kinematicsConfig.isEnabled) {
                                        val showH1Overlay by liveVm.showH1Overlay.collectAsState()
                                        val projectedOrder by liveVm.projectedOrder.collectAsState()
                                        val ordLabel =
                                            if (projectedOrder % 1.0 ==
                                                0.0
                                            ) {
                                                "H${projectedOrder.toInt()}"
                                            } else {
                                                "H%.1f".format(projectedOrder)
                                            }

                                        FilterChip(
                                            selected = showH1Overlay,
                                            onClick = { liveVm.toggleH1Overlay() },
                                            label = { Text("👁️ $ordLabel", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            trailingIcon = {
                                                Text(
                                                    text = "⚙️",
                                                    fontSize = 12.sp,
                                                    modifier =
                                                        Modifier
                                                            .padding(start = 4.dp)
                                                            .clickable { showProjectedOrderDialog = true },
                                                )
                                            },
                                            shape =
                                                androidx.compose.foundation.shape
                                                    .RoundedCornerShape(12.dp),
                                            colors =
                                                FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = NvhAccent.copy(alpha = 0.2f),
                                                    selectedLabelColor = NvhAccent,
                                                    containerColor = Color.Transparent,
                                                    labelColor = NvhOnSurfaceVariant,
                                                ),
                                            border =
                                                FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = showH1Overlay,
                                                    borderColor = MaterialTheme.colorScheme.outline,
                                                    selectedBorderColor = NvhAccent,
                                                ),
                                            modifier = Modifier.height(26.dp),
                                        )
                                    }

                                    // LED GPS — ou l'état d'autorisation quand il n'y en a pas [U1]
                                    if (isWavMode || permissions.metrologicalLocation) {
                                        GpsLedIndicator(status = telemetry.gpsStatus)
                                    } else {
                                        LocationPermissionChip(
                                            coarseOnly = permissions.anyLocation,
                                            onClick = { context.openAppSettings() },
                                        )
                                    }
                                }

                                // [U1, plan 4.1] Live speed needs precise location. Say so where
                                // the speed would be, with the one action that fixes it — the
                                // old build simply showed "--" for ever with no explanation.
                                if (!isWavMode && !permissions.metrologicalLocation) {
                                    Surface(
                                        color = NvhNoticeContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NvhNoticeBorder),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { context.openAppSettings() },
                                    ) {
                                        Text(
                                            text =
                                                if (permissions.anyLocation) {
                                                    "📍 Localisation approximative seulement — vitesse GNSS, RPM et " +
                                                        "suivi d'ordre indisponibles. Toucher pour ouvrir les réglages."
                                                } else {
                                                    "📍 Localisation non autorisée — vitesse GNSS, RPM et suivi " +
                                                        "d'ordre indisponibles. Toucher pour ouvrir les réglages."
                                                },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NvhOnNotice,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        )
                                    }
                                }

                                // Encart des valeurs instantanées (Vitesse, Accélération, Ordre Traqué)
                                val ordVal = kinematicsConfig.selectedTrackedOrder
                                val ordLabel = if (ordVal % 1.0 == 0.0) "H${ordVal.toInt()}" else "H%.1f".format(ordVal)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    val isGMPe = kinematicsConfig.isEnabled
                                    // [GPS-1.1 gate] Zero and unavailable are different states:
                                    // no exploitable fix or an expired estimate shows "--",
                                    // never a stale number that looks like a measurement.
                                    val gpsSpeedText =
                                        if (telemetry.gpsStatus == GpsStatus.NONE) {
                                            "--"
                                        } else {
                                            "%.1f".format(telemetry.speedKmh)
                                        }
                                    val theoInvalid =
                                        telemetry.speedValidity == EstimateValidity.INVALID
                                    val theoSpeedText =
                                        if (theoInvalid) {
                                            "--"
                                        } else {
                                            "%.1f".format(telemetry.theoreticalSpeedKmh)
                                        }
                                    if (isGMPe) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Vitesse",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NvhOnSurfaceVariant,
                                            )
                                            Text(
                                                text = "GPS: $gpsSpeedText",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhOnSurface,
                                            )
                                            Text(
                                                text = "Théo: $theoSpeedText",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhTheoretical,
                                            )
                                        }
                                    } else {
                                        KpiItem("Vitesse", "$gpsSpeedText km/h")
                                    }
                                    KpiItem("Accélération", String.format("%.2f g", telemetry.accelerationG))
                                    var throttledOrderDbFS by remember { mutableDoubleStateOf(-120.0) }
                                    var lastOrderUpdateTime by remember { mutableLongStateOf(0L) }

                                    val currentMillis = System.currentTimeMillis()
                                    if (currentMillis - lastOrderUpdateTime > 500 ||
                                        kotlin.math.abs(throttledOrderDbFS - telemetry.trackedOrderDbFS) > 30.0
                                    ) {
                                        throttledOrderDbFS = telemetry.trackedOrderDbFS
                                        lastOrderUpdateTime = currentMillis
                                    }

                                    KpiItem(
                                        "Ordre $ordLabel",
                                        when {
                                            !kinematicsConfig.isEnabled -> "Inactif"
                                            telemetry.speedKmh <= 1.0f -> "/"
                                            // [GPS-4.2] Search window wider than the
                                            // identifiability bound: suspended, never
                                            // an ambiguous number.
                                            !telemetry.trackedOrderIdentifiable -> "Non identifiable"
                                            else -> String.format("%.1f dBFS", throttledOrderDbFS)
                                        },
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                                // Onglets Sélecteurs de métrique pour le graphique 2D
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TelemetryMetric.values().forEach { metric ->
                                        val isOrderMetric = (metric == TelemetryMetric.ORDER)
                                        val isSelected = (selectedMetric == metric)
                                        val chipText =
                                            if (isOrderMetric && kinematicsConfig.isEnabled) {
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
                                                    liveVm.selectMetric(metric)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = chipText,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                )
                                            },
                                        )
                                    }
                                }

                                // Zone Graphique 2D synchronisé 1-to-1
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(NvhCanvas),
                                ) {
                                    TelemetryGraph(
                                        history = telemetryHistory,
                                        metric = selectedMetric,
                                        timeWindowSec =
                                            if (isWavMode &&
                                                loadedWavData != null
                                            ) {
                                                ((loadedWavData?.durationMs ?: 0L) / 1000.0)
                                            } else {
                                                timeWindowSec
                                            },
                                        historySize =
                                            if (isWavMode &&
                                                fftHistoryAbsolute.isNotEmpty()
                                            ) {
                                                fftHistoryAbsolute.size
                                            } else {
                                                session.historySize
                                            },
                                        ttnrSpectrum = latestTTNRSpectrum,
                                        minFreq = minFreq,
                                        maxFreq = maxFreq,
                                        sampleRate = analysisSampleRate,
                                        isKinematicsEnabled = kinematicsConfig.isEnabled,
                                        selectedOrderName = ordLabel,
                                        isWavAnalyzerMode = isWavMode,
                                        wavPlaybackProgress = wavProgress,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showInfoDialog) {
                InfoDialog(
                    onDismiss = { showInfoDialog = false },
                )
            }

            if (showOrderSelectionDialog) {
                OrderSelectionDialog(
                    currentOrder = kinematicsConfig.selectedTrackedOrder,
                    onOrderSelected = { newOrd ->
                        analyzerVm.updateSelectedTrackedOrder(newOrd)
                    },
                    onDismiss = { showOrderSelectionDialog = false },
                )
            }

            if (showProjectedOrderDialog) {
                val projectedOrder by liveVm.projectedOrder.collectAsState()
                OrderSelectionDialog(
                    currentOrder = projectedOrder,
                    onOrderSelected = { newOrd ->
                        liveVm.setProjectedOrder(newOrd)
                    },
                    onDismiss = { showProjectedOrderDialog = false },
                )
            }

            if (showSettingsDialog) {
                com.example.nvhspectro.ui.SettingsDialog(
                    onDismiss = { showSettingsDialog = false },
                    sampleRateHz = analysisSampleRate,
                    activeFilters = activeFilters,
                    onAddFilter = { filter -> analyzerVm.addAudioFilter(filter) },
                    onRemoveFilter = { filterId -> analyzerVm.removeAudioFilter(filterId) },
                    minDb = minDb,
                    maxDb = maxDb,
                    onMinDbChange = { liveVm.updateSettings(it, maxDb, fftSize, minFreq, maxFreq, timeWindowSec) },
                    onMaxDbChange = { liveVm.updateSettings(minDb, it, fftSize, minFreq, maxFreq, timeWindowSec) },
                    fftSize = fftSize,
                    onFftSizeChange = { liveVm.updateSettings(minDb, maxDb, it, minFreq, maxFreq, timeWindowSec) },
                    minFreq = minFreq,
                    onMinFreqChange = { liveVm.updateSettings(minDb, maxDb, fftSize, it, maxFreq, timeWindowSec) },
                    maxFreq = maxFreq,
                    onMaxFreqChange = { liveVm.updateSettings(minDb, maxDb, fftSize, minFreq, it, timeWindowSec) },
                    timeWindowSec = timeWindowSec,
                    onTimeWindowChange = { liveVm.updateSettings(minDb, maxDb, fftSize, minFreq, maxFreq, it) },
                    isDetectorEnabled = isDetectorEnabled,
                    onDetectorEnabledChange = { enabled ->
                        liveVm.updateDetectorSettings(enabled, emergenceThresholdDb, magnitudeGateDbFS)
                    },
                    emergenceThresholdDb = emergenceThresholdDb,
                    onEmergenceThresholdChange = { threshold ->
                        liveVm.updateDetectorSettings(isDetectorEnabled, threshold, magnitudeGateDbFS)
                    },
                    magnitudeGateDbFS = magnitudeGateDbFS,
                    onMagnitudeGateChange = { gate ->
                        liveVm.updateDetectorSettings(isDetectorEnabled, emergenceThresholdDb, gate)
                    },
                    isWavAnalyzerMode = isWavMode,
                    wavDurationSec = (loadedWavData?.durationMs ?: 0L) / 1000.0,
                )
            }

            if (showExportDialog) {
                com.example.nvhspectro.ui.ExportDialog(
                    onDismiss = { showExportDialog = false },
                    telemetry = telemetry,
                    onExport = { pedalPercent, comments ->
                        showExportDialog = false
                        reportVm.exportData(pedalPercent, comments)
                    },
                )
            }

            if (showKinematicsDialog) {
                com.example.nvhspectro.ui.KinematicsDialog(
                    currentConfig = kinematicsConfig,
                    onDismiss = { showKinematicsDialog = false },
                    onSave = { newConfig ->
                        showKinematicsDialog = false
                        analyzerVm.updateKinematicsConfig(newConfig)
                    },
                )
            }

            if (showEmergenceReportDialog) {
                com.example.nvhspectro.ui.EmergenceReportDialog(
                    entries = emergenceReportEntries,
                    kinematicsConfig = kinematicsConfig,
                    onDismiss = { showEmergenceReportDialog = false },
                    onClearReport = { session.clearEmergenceReport() },
                )
            }

            if (showSaveRecordingDialog) {
                com.example.nvhspectro.ui.SaveRecordingDialog(
                    durationSec = recordingElapsedSec,
                    onSave = { customName ->
                        liveVm.saveAudioRecording(customName)
                    },
                    onDismiss = {
                        liveVm.cancelSaveAudioRecording()
                    },
                )
            }

            if (showWavSelectionDialog) {
                com.example.nvhspectro.ui.WavSelectionDialog(
                    onDismiss = { showWavSelectionDialog = false },
                    onSelectEntry = { wavUri, jsonUri ->
                        showWavSelectionDialog = false
                        analyzerVm.loadWavFromUri(context, wavUri, jsonUri)
                    },
                    onImportExternal = {
                        showWavSelectionDialog = false
                        wavPickerLauncher.launch("audio/*")
                    },
                )
            }
        }
    }
}

@Composable
fun GpsLedIndicator(status: GpsStatus) {
    val (ledColor, textLabel) =
        when (status) {
            GpsStatus.GOOD -> NvhStatusGood to "Signal OK"
            GpsStatus.POOR -> NvhStatusWarn to "Signal Médiocre"
            GpsStatus.NONE -> NvhStatusBad to "Signal Perdu"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "Signal GPS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(color = ledColor, shape = CircleShape),
        )
        Text(text = textLabel, style = MaterialTheme.typography.labelSmall, color = NvhOnSurfaceVariant)
    }
}

/**
 * Replaces the GNSS LED when the app has no precise-location grant [U1, plan 4.1].
 *
 * A red LED would claim "signal lost" for something that is not a signal problem at all; the
 * operator needs to know it is a permission, and be able to act on it from here.
 */
@Composable
fun LocationPermissionChip(
    coarseOnly: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(text = "Signal GPS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(text = "⛔", fontSize = 12.sp)
        Text(
            text = if (coarseOnly) "Précision refusée" else "Non autorisé",
            style = MaterialTheme.typography.labelSmall,
            color = NvhStatusWarn,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun KpiItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = NvhOnSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
