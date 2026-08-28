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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.nvhspectro.ui.TelemetryGraph
import com.example.nvhspectro.ui.TelemetryMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    // [U8, plan 4.9] No in-app splash: the platform SplashScreen (installed in MainActivity)
    // covers process start and hands over at the first frame. The old Compose splash added a
    // fixed 2 s delay on top of it.
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

    val telemetry by session.telemetryState.collectAsStateWithLifecycle()
    val telemetryHistory by session.telemetryHistory.collectAsStateWithLifecycle()
    val selectedMetric by liveVm.selectedMetric.collectAsStateWithLifecycle()

    val fftHistory by session.fftHistory.collectAsStateWithLifecycle()
    val fftHistoryAbsolute by session.fftHistoryAbsolute.collectAsStateWithLifecycle()
    val fftHistoryTTNR by session.fftHistoryTTNR.collectAsStateWithLifecycle()
    val isDetectorEnabled by session.isDetectorEnabled.collectAsStateWithLifecycle()
    val emergenceThresholdDb by session.emergenceThresholdDb.collectAsStateWithLifecycle()
    val magnitudeGateDbFS by session.magnitudeGateDbFS.collectAsStateWithLifecycle()
    val latestTTNRSpectrum by session.latestTTNRSpectrum.collectAsStateWithLifecycle()

    val minDb by session.minDb.collectAsStateWithLifecycle()
    val maxDb by session.maxDb.collectAsStateWithLifecycle()
    val fftSize by session.fftSize.collectAsStateWithLifecycle()
    val minFreq by session.minFreq.collectAsStateWithLifecycle()
    val maxFreq by session.maxFreq.collectAsStateWithLifecycle()
    val timeWindowSec by session.timeWindowSec.collectAsStateWithLifecycle()
    val displayMode by session.displayMode.collectAsStateWithLifecycle()
    val isFrozen by session.isFrozen.collectAsStateWithLifecycle()

    val kinematicsConfig by session.kinematicsConfig.collectAsStateWithLifecycle()
    val activeFilters by analyzerVm.activeFilters.collectAsStateWithLifecycle()
    val trackedHarmonicTags by session.trackedHarmonicTags.collectAsStateWithLifecycle()
    val emergenceReportEntries by session.emergenceReportEntries.collectAsStateWithLifecycle()

    val isAudioRecording by liveVm.isAudioRecording.collectAsStateWithLifecycle()
    val recordingElapsedSec by liveVm.recordingElapsedSec.collectAsStateWithLifecycle()
    val showSaveRecordingDialog by liveVm.showSaveRecordingDialog.collectAsStateWithLifecycle()

    val audioSourceMode by session.audioSourceMode.collectAsStateWithLifecycle()
    var showAudioModeMenu by remember { mutableStateOf(false) }
    var showWavSelectionDialog by remember { mutableStateOf(false) }
    val loadedWavData by session.loadedWavData.collectAsStateWithLifecycle()
    val analysisNotice by session.analysisNotice.collectAsStateWithLifecycle()
    val loadedWavFileName by analyzerVm.loadedWavFileName.collectAsStateWithLifecycle()
    val wavPlaybackPositionMs by analyzerVm.player.positionMs.collectAsStateWithLifecycle()
    val isWavPlaying by analyzerVm.player.isPlaying.collectAsStateWithLifecycle()
    val isReportModeActive by reportVm.isReportModeActive.collectAsStateWithLifecycle()

    val loadedVideoUri by analyzerVm.loadedVideoUri.collectAsStateWithLifecycle()
    val loadedVideoTitle by analyzerVm.loadedVideoTitle.collectAsStateWithLifecycle()
    val processingEstimateMessage by analyzerVm.processingEstimateMessage.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    // Hoisted out of the click lambda: resources are read in composition, not in a callback.
    val liveDeniedMessage = stringResource(R.string.live_unavailable_no_mic)
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
                    title = {
                        // [§12, plan 4.4] At font scale 1.3 an unconstrained title wrapped to
                        // three lines and ran under the logo; it truncates instead.
                        Text(
                            text = stringResource(R.string.app_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        Image(
                            painter = painterResource(id = R.drawable.logo_vibratec),
                            contentDescription = stringResource(R.string.cd_logo_vibratec),
                            modifier =
                                Modifier
                                    .height(28.dp)
                                    .padding(end = 6.dp),
                            contentScale = ContentScale.Fit,
                        )
                        TextButton(onClick = { showInfoDialog = true }) {
                            Text(
                                stringResource(R.string.action_info),
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        TextButton(onClick = { showSettingsDialog = true }) {
                            Text(stringResource(R.string.action_settings), color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                                text =
                                    stringResource(
                                        if (kinematicsConfig.isEnabled) R.string.gmpe_button_active else R.string.gmpe_button,
                                    ),
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
                                text = stringResource(if (isReportModeActive) R.string.report_mode_exit else R.string.report_mode_enter),
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
                                                .height(MIN_TOUCH_TARGET),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = NvhExport,
                                                contentColor = NvhOnSurface,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.export_frozen),
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
                                        .height(MIN_TOUCH_TARGET),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = if (isFrozen) NvhRecording else MaterialTheme.colorScheme.secondary,
                                        contentColor = if (isFrozen) NvhOnSurface else MaterialTheme.colorScheme.onSecondary,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(if (isFrozen) R.string.unfreeze else R.string.freeze),
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
                                                        liveDeniedMessage,
                                                    )
                                                }
                                                showAudioModeMenu = false
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(MIN_TOUCH_TARGET),
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
                                                text =
                                                    stringResource(
                                                        if (permissions.liveCapture) R.string.source_live else R.string.source_live_denied,
                                                    ),
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
                                                    .height(MIN_TOUCH_TARGET),
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
                                                text = stringResource(R.string.source_wav),
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
                                                    .height(MIN_TOUCH_TARGET),
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
                                                text = stringResource(R.string.source_video),
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
                                        .height(MIN_TOUCH_TARGET),
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
                                            com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER ->
                                                stringResource(
                                                    R.string.source_button_wav,
                                                )
                                            com.example.nvhspectro.AudioSourceMode.VIDEO -> stringResource(R.string.source_button_video)
                                            else -> stringResource(R.string.source_button_live)
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
            // [U8, plan 4.9] The two panes are declared once and arranged by the container:
            // stacked in portrait, side by side in landscape. targetSdk 36 ignores an
            // orientation lock on large screens, so a tablet used to get the portrait stack
            // squeezed into a wide window — a ~200 px spectrogram. Closures capture the state
            // the panes need, so adapting the layout costs no parameter plumbing.
            val spectrogramPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Box(
                    modifier = paneModifier.background(NvhCanvas),
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
                        showH1Overlay = liveVm.showH1Overlay.collectAsStateWithLifecycle().value,
                        projectedOrder = liveVm.projectedOrder.collectAsStateWithLifecycle().value,
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
                                                    stringResource(R.string.load_video)
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
                                                    stringResource(R.string.loaded_file, loadedWavFileName!!.take(WAV_NAME_MAX_CHARS))
                                                } else {
                                                    stringResource(R.string.load_wav)
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
                                            val secStr = formatMinSec(recordingElapsedSec)
                                            Text(
                                                stringResource(
                                                    R.string.recording_stop,
                                                    secStr,
                                                    formatMinSec(LiveViewModel.MAX_RECORDING_SEC),
                                                ),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhOnSurface,
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.recording_start),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhOnSurface,
                                            )
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
                                stringResource(R.string.range_emergence)
                            } else {
                                stringResource(R.string.range_absolute, minDb.toInt(), maxDb.toInt())
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
                                    val titleText = kinematicsConfig.vehicleName.ifEmpty { stringResource(R.string.gmpe_default_name) }

                                    if (isActiveSpeed) {
                                        val h1Hz = kinematicsConfig.calculateH1FreqHz(curSpeed)
                                        val curRpm = kinematicsConfig.calculateRpm(curSpeed).toInt()
                                        Text(
                                            text = stringResource(R.string.gmpe_banner_active, titleText, effV1000, h1Hz, curRpm),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhOnSurface,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.gmpe_banner_idle, titleText, effV1000),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NvhStatusWarn,
                                        )
                                    }

                                    // [A4, plan 4.6, D6] The Emergence Report's entry point.
                                    // The accumulation logic never stopped working; the button
                                    // that opened it was lost in a refactor, so a finished
                                    // feature has been unreachable ever since. The count makes
                                    // it obvious there is something to look at.
                                    EmergenceReportButton(
                                        entryCount = emergenceReportEntries.size,
                                        onClick = { showEmergenceReportDialog = true },
                                    )
                                }
                            }

                            // 2. Bannière additionnelle des Harmoniques Cibles (si renseignées)
                            val targetOrdersList = kinematicsConfig.parsedTargetOrders()
                            if (targetOrdersList.isNotEmpty()) {
                                val targetStr =
                                    targetOrdersList.joinToString(", ") { orderLabel(context, it) }

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
                                            text = stringResource(R.string.target_orders_banner, targetStr),
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
                                modifier =
                                    Modifier
                                        .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
                                        .clickable(
                                            onClickLabel = stringResource(R.string.notice_dismiss),
                                            onClick = { session.dismissNotice() },
                                        ),
                            ) {
                                Text(
                                    text = stringResource(R.string.notice_with_close, notice),
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
                                text = stringResource(R.string.no_wav_loaded),
                                color = NvhOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else if (fftHistory.isEmpty()) {
                        Text(stringResource(R.string.analysis_in_progress), color = NvhOnSurface)
                    }
                }
            }

            val vehicleDataPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(modifier = paneModifier) {
                    // Lecteur WAV (si un fichier est chargé en mode Analyseur WAV)
                    if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER && loadedWavData != null) {
                        com.example.nvhspectro.ui.WavPlayerBar(
                            fileName = loadedWavFileName ?: stringResource(R.string.default_wav_name),
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
                                        text = stringResource(R.string.gps_data_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )

                                    // Bouton 👁️ Hx intégré à l'en-tête (Si GMPe activé)
                                    if (kinematicsConfig.isEnabled) {
                                        val showH1Overlay by liveVm.showH1Overlay.collectAsStateWithLifecycle()
                                        val projectedOrder by liveVm.projectedOrder.collectAsStateWithLifecycle()
                                        val ordLabel = orderLabel(context, projectedOrder)
                                        val h1OverlayLabel = stringResource(R.string.cd_h1_overlay, ordLabel)
                                        val projectedOrderLabel = stringResource(R.string.cd_choose_projected_order)

                                        FilterChip(
                                            selected = showH1Overlay,
                                            onClick = { liveVm.toggleH1Overlay() },
                                            label = {
                                                Text(
                                                    stringResource(R.string.h1_overlay_chip, ordLabel),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier =
                                                        Modifier.semantics {
                                                            contentDescription = h1OverlayLabel
                                                        },
                                                )
                                            },
                                            trailingIcon = {
                                                Text(
                                                    text = "⚙️",
                                                    fontSize = 12.sp,
                                                    modifier =
                                                        Modifier
                                                            .padding(start = 4.dp)
                                                            .semantics {
                                                                contentDescription = projectedOrderLabel
                                                            }.clickable(
                                                                onClickLabel = projectedOrderLabel,
                                                                onClick = { showProjectedOrderDialog = true },
                                                            ),
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
                                            // No fixed height: the chip must be free to grow
                                            // with the font scale and keep its 48 dp target.
                                            modifier = Modifier.defaultMinSize(minHeight = MIN_TOUCH_TARGET),
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
                                                    stringResource(R.string.location_coarse_banner)
                                                } else {
                                                    stringResource(R.string.location_denied_banner)
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
                                val ordLabel = orderLabel(context, ordVal)

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
                                            stringResource(R.string.value_unavailable)
                                        } else {
                                            formatSpeed(telemetry.speedKmh)
                                        }
                                    val theoInvalid =
                                        telemetry.speedValidity == EstimateValidity.INVALID
                                    val theoSpeedText =
                                        if (theoInvalid) {
                                            stringResource(R.string.value_unavailable)
                                        } else {
                                            formatSpeed(telemetry.theoreticalSpeedKmh)
                                        }
                                    if (isGMPe) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.kpi_speed),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NvhOnSurfaceVariant,
                                            )
                                            Text(
                                                text = stringResource(R.string.kpi_speed_gps, gpsSpeedText),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhOnSurface,
                                            )
                                            Text(
                                                text = stringResource(R.string.kpi_speed_theoretical, theoSpeedText),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NvhTheoretical,
                                            )
                                        }
                                    } else {
                                        KpiItem(
                                            stringResource(R.string.kpi_speed),
                                            stringResource(R.string.kpi_speed_with_unit, gpsSpeedText),
                                        )
                                    }
                                    KpiItem(
                                        stringResource(R.string.kpi_acceleration),
                                        stringResource(R.string.kpi_acceleration_value, telemetry.accelerationG),
                                    )
                                    // [U2, plan 4.9] Sampled in the ViewModel: composition
                                    // no longer reads the clock or mutates remembered state.
                                    val throttledOrderDbFS by liveVm.displayedOrderDbFS.collectAsStateWithLifecycle()

                                    KpiItem(
                                        stringResource(R.string.kpi_order, ordLabel),
                                        when {
                                            !kinematicsConfig.isEnabled -> stringResource(R.string.kpi_order_inactive)
                                            telemetry.speedKmh <= 1.0f -> stringResource(R.string.kpi_order_stopped)
                                            // [GPS-4.2] Search window wider than the
                                            // identifiability bound: suspended, never
                                            // an ambiguous number.
                                            !telemetry.trackedOrderIdentifiable -> stringResource(R.string.kpi_order_unidentifiable)
                                            else -> stringResource(R.string.kpi_order_value, throttledOrderDbFS)
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
                                                stringResource(R.string.metric_order_with_settings, ordLabel)
                                            } else {
                                                stringResource(metric.labelRes)
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

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // Landscape / tablet: side by side, so the spectrogram keeps a usable height.
                if (maxWidth > maxHeight) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        spectrogramPane(Modifier.weight(SPECTRO_PANE_WEIGHT).fillMaxHeight())
                        vehicleDataPane(Modifier.weight(DATA_PANE_WEIGHT).fillMaxHeight())
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        spectrogramPane(Modifier.weight(SPECTRO_PANE_WEIGHT).fillMaxWidth())
                        vehicleDataPane(Modifier.weight(DATA_PANE_WEIGHT).fillMaxWidth())
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
                val projectedOrder by liveVm.projectedOrder.collectAsStateWithLifecycle()
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
    // [§12, plan 4.4] Colour is never the only channel: each state also has its own SHAPE
    // (filled circle / triangle / cross) and its own words. A red-green LED alone is
    // unreadable to a red-green colour-blind operator — and this LED is what tells them
    // whether to trust the RPM numbers.
    val (ledColor, textLabel, glyph) =
        when (status) {
            GpsStatus.GOOD -> Triple(NvhStatusGood, stringResource(R.string.gps_signal_good), "●")
            GpsStatus.POOR -> Triple(NvhStatusWarn, stringResource(R.string.gps_signal_poor), "▲")
            GpsStatus.NONE -> Triple(NvhStatusBad, stringResource(R.string.gps_signal_none), "✕")
        }

    val spoken = stringResource(R.string.cd_gps_signal, textLabel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Text(text = stringResource(R.string.gps_signal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(text = glyph, color = ledColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(text = textLabel, style = MaterialTheme.typography.labelSmall, color = NvhOnSurfaceVariant)
    }
}

/**
 * Opens the harmonic Emergence Report [A4, plan 4.6, decision D6].
 *
 * Sits in the GMPe banner because that is the context the report describes: it exists only
 * while the kinematic chain is engaged, and its badge shows how many harmonics have been
 * characterised so far.
 */
@Composable
fun EmergenceReportButton(
    entryCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = if (entryCount > 0) NvhAccent.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NvhAccent.copy(alpha = 0.7f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text =
                if (entryCount > 0) {
                    stringResource(R.string.emergence_report_button_count, entryCount)
                } else {
                    stringResource(R.string.emergence_report_button)
                },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = NvhAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
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
        Text(text = stringResource(R.string.gps_signal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(text = "⛔", fontSize = 12.sp)
        Text(
            text = stringResource(if (coarseOnly) R.string.gps_permission_coarse else R.string.gps_permission_denied),
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

/**
 * Minimum interactive size [§12, plan 4.4].
 *
 * The bottom bar and the source menu used 34–38 dp buttons: below the 48 dp Material/WCAG
 * target, and fixed heights that clip their own label once the user raises the font scale.
 * This is the floor for every control an operator has to hit — often wearing gloves, in a
 * moving vehicle.
 */
private val MIN_TOUCH_TARGET = 48.dp

/** Spectrogram share of the main layout, in both orientations [U8, plan 4.9]. */
private const val SPECTRO_PANE_WEIGHT = 0.55f

/** Vehicle-data / telemetry share of the main layout. */
private const val DATA_PANE_WEIGHT = 0.45f

/** Loaded-file chip truncation, so a long name cannot push the mode chips off-screen. */
private const val WAV_NAME_MAX_CHARS = 14

/** mm:ss, locale-independent: a timer readout must be identical on every device [C11 class]. */
private fun formatMinSec(totalSeconds: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)

/**
 * "H18" / "H7.4" — the one place an order is turned into a label [§12, plan 4.4].
 *
 * The same two-branch formatting was repeated at four call sites, each with its own literal.
 */
private fun orderLabel(
    context: android.content.Context,
    order: Double,
): String =
    if (order % 1.0 == 0.0) {
        context.getString(R.string.order_integer, order.toInt())
    } else {
        context.getString(R.string.order_fractional, order)
    }

/** One decimal, locale-independent — a measurement readout must not change with the locale. */
private fun formatSpeed(kmh: Float): String = String.format(java.util.Locale.ROOT, "%.1f", kmh)
