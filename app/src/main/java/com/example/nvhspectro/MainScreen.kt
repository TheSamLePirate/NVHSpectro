package com.example.nvhspectro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nvhspectro.data.EstimateValidity
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhCanvasChipBorder
import com.example.nvhspectro.theme.NvhCanvasPanel
import com.example.nvhspectro.theme.NvhCanvasScrim
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhMinTouchTarget
import com.example.nvhspectro.theme.NvhModeVideo
import com.example.nvhspectro.theme.NvhModeVideoAccent
import com.example.nvhspectro.theme.NvhModeWav
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhNoticeBorder
import com.example.nvhspectro.theme.NvhNoticeContainer
import com.example.nvhspectro.theme.NvhOnNotice
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhReadoutLarge
import com.example.nvhspectro.theme.NvhReadoutMedium
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhRecording
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhStatusBad
import com.example.nvhspectro.theme.NvhStatusGood
import com.example.nvhspectro.theme.NvhStatusWarn
import com.example.nvhspectro.theme.NvhTheoretical
import com.example.nvhspectro.ui.InfoDialog
import com.example.nvhspectro.ui.MainBottomBar
import com.example.nvhspectro.ui.NvhGlyphShape
import com.example.nvhspectro.ui.NvhPermissions
import com.example.nvhspectro.ui.NvhStatusGlyph
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

    // [V14 UX-D6] Immersive mode: the measurement surface can take the whole screen; the
    // chrome (bars + telemetry pane) comes back with one tap.
    var isImmersive by rememberSaveable { mutableStateOf(false) }

    // [V14 UX-D7] User-adjustable pane split, persisted across rotation.
    var splitFraction by rememberSaveable { mutableFloatStateOf(DEFAULT_SPECTRO_PANE_FRACTION) }

    // [V14 UX-M8] Entering/leaving report mode was a hard full-screen cut; it now crossfades.
    AnimatedContent(
        targetState = isReportModeActive,
        transitionSpec = {
            fadeIn(tween(MODE_TRANSITION_MS)) togetherWith fadeOut(tween(MODE_TRANSITION_MS))
        },
        label = "reportModeTransition",
    ) { reportActive ->
        if (reportActive) {
            com.example.nvhspectro.ui
                .ReportModeScreen(viewModel = reportVm, onBack = { reportVm.toggleReportMode() })
        } else {
            Scaffold(
                topBar = {
                    if (!isImmersive) {
                        MainTopBar(
                            onShowInfo = { showInfoDialog = true },
                            onShowSettings = { showSettingsDialog = true },
                        )
                    }
                },
                bottomBar = {
                    if (!isImmersive) {
                        MainBottomBar(
                            isKinematicsEnabled = kinematicsConfig.isEnabled,
                            isReportModeActive = isReportModeActive,
                            isFrozen = isFrozen,
                            audioSourceMode = audioSourceMode,
                            isVideoMode = isVideoMode,
                            isLiveCaptureAllowed = permissions.liveCapture,
                            onOpenKinematics = { showKinematicsDialog = true },
                            onToggleReportMode = { reportVm.toggleReportMode() },
                            onToggleFreeze = { session.toggleFreeze() },
                            onSelectAudioMode = { mode -> session.setAudioSourceMode(mode) },
                            onLiveDenied = { session.postNotice(liveDeniedMessage) },
                        )
                    }
                },
                floatingActionButton = {
                    // [V14 UX-B1] The export action no longer floats on a pixel-offset
                    // Popup: it is a FAB that appears when (and only when) a view is frozen.
                    AnimatedVisibility(
                        visible = isFrozen,
                        enter = scaleIn(tween(MODE_TRANSITION_MS)) + fadeIn(tween(MODE_TRANSITION_MS)),
                        exit = scaleOut(tween(MODE_TRANSITION_MS)) + fadeOut(tween(MODE_TRANSITION_MS)),
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { showExportDialog = true },
                            icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                            text = { Text(stringResource(R.string.export_frozen)) },
                            containerColor = NvhExport,
                            contentColor = NvhOnSurface,
                        )
                    }
                },
            ) { paddingValues ->
                // [U8, plan 4.9] The two panes are declared once and arranged by the container:
                // stacked on compact-width portrait, side by side otherwise. Closures capture
                // the state the panes need, so adapting the layout costs no parameter plumbing.
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

                        // Superposition en haut à gauche : sélecteur de mode, bannières GMPe,
                        // bandeau d'analyse.
                        Column(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    // End inset: the fullscreen toggle owns the top-right
                                    // corner; the chip row must not slide under it.
                                    .padding(
                                        start = NvhSpacing.sm,
                                        top = NvhSpacing.xs,
                                        end = NvhMinTouchTarget + NvhSpacing.sm,
                                    ),
                            verticalArrangement = Arrangement.spacedBy(NvhSpacing.xxs),
                        ) {
                            // Sélecteur de Mode (Absolue vs TTNR) + action contextuelle.
                            // Scrollable: à petite largeur ou grande échelle de police les
                            // chips défilent au lieu de se tronquer ou de se replier.
                            Row(
                                modifier =
                                    Modifier.horizontalScroll(
                                        androidx.compose.foundation.rememberScrollState(),
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                            ) {
                                DisplayMode.values().forEach { mode ->
                                    FilterChip(
                                        selected = (displayMode == mode),
                                        onClick = { session.setDisplayMode(mode) },
                                        label = {
                                            Text(
                                                mode.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                            )
                                        },
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

                                // Charger vidéo (Vidéo) OU charger WAV (Analyseur) OU
                                // enregistrer (Direct).
                                if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.VIDEO) {
                                    FilterChip(
                                        selected = (loadedVideoUri != null),
                                        onClick = { videoPickerLauncher.launch("video/*") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Videocam,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        },
                                        label = {
                                            Text(
                                                text =
                                                    if (loadedVideoTitle.isNotBlank()) {
                                                        loadedVideoTitle
                                                    } else {
                                                        stringResource(R.string.load_video)
                                                    },
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = CHIP_LABEL_MAX_WIDTH),
                                            )
                                        },
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NvhModeVideo,
                                                selectedLabelColor = NvhOnSurface,
                                                selectedLeadingIconColor = NvhOnSurface,
                                                containerColor = NvhCanvasScrim,
                                                labelColor = NvhOnSurface,
                                                iconColor = NvhOnSurface,
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
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.FolderOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        },
                                        label = {
                                            // [V14 UX-N4] Truncated by layout (ellipsis), not
                                            // by a character count blind to the actual width.
                                            Text(
                                                text = loadedWavFileName ?: stringResource(R.string.load_wav),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = CHIP_LABEL_MAX_WIDTH),
                                            )
                                        },
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NvhModeWav,
                                                selectedLabelColor = NvhOnSurface,
                                                selectedLeadingIconColor = NvhOnSurface,
                                                containerColor = NvhCanvasScrim,
                                                labelColor = NvhOnSurface,
                                                iconColor = NvhOnSurface,
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
                                        leadingIcon = {
                                            Icon(
                                                imageVector =
                                                    if (isAudioRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                                contentDescription = null,
                                                tint = if (isAudioRecording) NvhOnSurface else NvhStatusBad,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        },
                                        label = {
                                            if (isAudioRecording) {
                                                val secStr = formatMinSec(recordingElapsedSec)
                                                Text(
                                                    stringResource(
                                                        R.string.recording_stop,
                                                        secStr,
                                                        formatMinSec(LiveViewModel.MAX_RECORDING_SEC),
                                                    ),
                                                    style = NvhReadoutSmall,
                                                    maxLines = 1,
                                                )
                                            } else {
                                                Text(
                                                    stringResource(R.string.recording_start),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                )
                                            }
                                        },
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NvhRecording,
                                                selectedLabelColor = NvhOnSurface,
                                                selectedLeadingIconColor = NvhOnSurface,
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

                            // Indication dynamique Min & Max
                            val rangeText =
                                if (displayMode == DisplayMode.TTNR) {
                                    stringResource(R.string.range_emergence)
                                } else {
                                    stringResource(R.string.range_absolute, minDb.toInt(), maxDb.toInt())
                                }
                            Surface(
                                color = NvhCanvasScrim,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = rangeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NvhOnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = NvhSpacing.xs, vertical = NvhSpacing.xxs),
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
                                    shape = MaterialTheme.shapes.extraSmall,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                                    ) {
                                        // LED d'état : ROUGE si Vitesse <= 1 km/h, VERTE sinon
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(BANNER_LED_SIZE)
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
                                                style = NvhReadoutSmall,
                                                color = NvhOnSurface,
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.gmpe_banner_idle, titleText, effV1000),
                                                style = NvhReadoutSmall,
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
                                        shape = MaterialTheme.shapes.extraSmall,
                                        border =
                                            androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                NvhAccent.copy(alpha = NvhAlpha.STRONG),
                                            ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(BANNER_DOT_SIZE)
                                                        .background(NvhAccent, CircleShape),
                                            )
                                            Text(
                                                text = stringResource(R.string.target_orders_banner, targetStr),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NvhAccent,
                                            )
                                        }
                                    }
                                }
                            }

                            // Bandeau analyse [C2/C3] : rejets de fichiers, troncature. Tap = fermer.
                            analysisNotice?.let { notice ->
                                Surface(
                                    color = NvhNoticeContainer,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NvhNoticeBorder),
                                    modifier =
                                        Modifier
                                            .defaultMinSize(minHeight = NvhMinTouchTarget)
                                            .clickable(
                                                onClickLabel = stringResource(R.string.notice_dismiss),
                                                onClick = { session.dismissNotice() },
                                            ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                                    ) {
                                        Icon(
                                            Icons.Outlined.WarningAmber,
                                            contentDescription = null,
                                            tint = NvhOnNotice,
                                            modifier = Modifier.size(NOTICE_ICON_SIZE),
                                        )
                                        Text(
                                            text = notice,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NvhOnNotice,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = NvhOnNotice,
                                            modifier = Modifier.size(NOTICE_ICON_SIZE),
                                        )
                                    }
                                }
                            }
                        }

                        // [V14 UX-D6] Plein écran : le canvas est la surface de mesure — il
                        // peut occuper tout l'écran, le chrome revient d'un tap.
                        val fullscreenLabel =
                            stringResource(if (isImmersive) R.string.cd_exit_fullscreen else R.string.cd_enter_fullscreen)
                        Surface(
                            color = NvhCanvasScrim,
                            shape = CircleShape,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(NvhSpacing.xs),
                        ) {
                            IconButton(onClick = { isImmersive = !isImmersive }) {
                                Icon(
                                    imageVector =
                                        if (isImmersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                    contentDescription = fullscreenLabel,
                                    tint = NvhOnSurface,
                                )
                            }
                        }

                        if (!processingEstimateMessage.isNullOrEmpty()) {
                            Surface(
                                color = NvhCanvasPanel,
                                shape = MaterialTheme.shapes.medium,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NvhAccent),
                                modifier = Modifier.padding(NvhSpacing.xl),
                            ) {
                                Column(
                                    modifier = Modifier.padding(NvhSpacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                                ) {
                                    CircularProgressIndicator(color = NvhAccent, modifier = Modifier.size(PROGRESS_SIZE))
                                    Text(
                                        text = processingEstimateMessage!!,
                                        color = NvhOnSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        } else if (audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER && loadedWavFileName == null) {
                            Surface(
                                color = NvhCanvasScrim,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(NvhSpacing.lg),
                            ) {
                                Column(
                                    modifier = Modifier.padding(NvhSpacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                                ) {
                                    Icon(
                                        Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = NvhOnSurfaceVariant,
                                        modifier = Modifier.size(EMPTY_STATE_ICON_SIZE),
                                    )
                                    Text(
                                        text = stringResource(R.string.no_wav_loaded),
                                        color = NvhOnSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        } else if (fftHistory.isEmpty()) {
                            Text(
                                stringResource(R.string.analysis_in_progress),
                                color = NvhOnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                val vehicleDataPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(modifier = paneModifier) {
                        // [V14 UX-M8] Le lecteur WAV apparaît/disparaît en glissant au lieu de
                        // pousser brutalement la carte télémétrie.
                        AnimatedVisibility(
                            visible =
                                audioSourceMode == com.example.nvhspectro.AudioSourceMode.WAV_ANALYZER &&
                                    loadedWavData != null,
                            enter = expandVertically(tween(MODE_TRANSITION_MS)) + fadeIn(tween(MODE_TRANSITION_MS)),
                            exit = shrinkVertically(tween(MODE_TRANSITION_MS)) + fadeOut(tween(MODE_TRANSITION_MS)),
                        ) {
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

                        // Zone 2: Données Véhicule / Télémétrie OU Lecteur Vidéo (Mode Vidéo).
                        // [V14 UX-M10] weight(1f) — le reste du panneau, une fois le lecteur
                        // servi. Les anciens weight(0.45f) sans fratrie pondérée étaient morts.
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
                                        .weight(1f),
                            )
                        } else {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(NvhSpacing.sm),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(NvhSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                                ) {
                                    // En-tête : Titre + Bouton Hx + LED Signal GPS
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.gps_data_title),
                                            style = MaterialTheme.typography.titleSmall,
                                        )

                                        // Calque Hx intégré à l'en-tête (si GMPe activé)
                                        if (kinematicsConfig.isEnabled) {
                                            val showH1Overlay by liveVm.showH1Overlay.collectAsStateWithLifecycle()
                                            val projectedOrder by liveVm.projectedOrder.collectAsStateWithLifecycle()
                                            val ordLabel = orderLabel(context, projectedOrder)
                                            val h1OverlayLabel = stringResource(R.string.cd_h1_overlay, ordLabel)
                                            val projectedOrderLabel = stringResource(R.string.cd_choose_projected_order)

                                            FilterChip(
                                                selected = showH1Overlay,
                                                onClick = { liveVm.toggleH1Overlay() },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Visibility,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        ordLabel,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier =
                                                            Modifier.semantics {
                                                                contentDescription = h1OverlayLabel
                                                            },
                                                    )
                                                },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Tune,
                                                        contentDescription = projectedOrderLabel,
                                                        modifier =
                                                            Modifier
                                                                .size(FilterChipDefaults.IconSize)
                                                                .clickable(
                                                                    onClickLabel = projectedOrderLabel,
                                                                    onClick = { showProjectedOrderDialog = true },
                                                                ),
                                                    )
                                                },
                                                colors =
                                                    FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = NvhAccent.copy(alpha = NvhAlpha.TINT),
                                                        selectedLabelColor = NvhAccent,
                                                        selectedLeadingIconColor = NvhAccent,
                                                        selectedTrailingIconColor = NvhAccent,
                                                        containerColor = Color.Transparent,
                                                        labelColor = NvhOnSurfaceVariant,
                                                        iconColor = NvhOnSurfaceVariant,
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
                                                modifier = Modifier.defaultMinSize(minHeight = NvhMinTouchTarget),
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
                                            shape = MaterialTheme.shapes.extraSmall,
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
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NvhOnNotice,
                                                modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs),
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
                                                    style = NvhReadoutMedium,
                                                    color = NvhOnSurface,
                                                )
                                                Text(
                                                    text = stringResource(R.string.kpi_speed_theoretical, theoSpeedText),
                                                    style = NvhReadoutMedium,
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
                                        modifier = Modifier.fillMaxWidth(),
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
                                                trailingIcon =
                                                    if (isOrderMetric && isSelected && kinematicsConfig.isEnabled) {
                                                        {
                                                            Icon(
                                                                Icons.Outlined.Tune,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                                            )
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                label = {
                                                    Text(
                                                        text = chipText,
                                                        style = MaterialTheme.typography.labelMedium,
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
                    // [V14 UX-M9] Width class first, aspect ratio as tiebreaker: a tablet held
                    // in portrait (>= 600 dp wide) gets the split, not the phone stack; a
                    // compact phone in landscape still splits so the spectrogram keeps height.
                    val splitHorizontally = maxWidth >= MEDIUM_WIDTH_BREAKPOINT || maxWidth > maxHeight
                    val density = LocalDensity.current
                    val totalPx =
                        with(density) { (if (splitHorizontally) maxWidth else maxHeight).toPx() }
                    val onSplitDrag: (Float) -> Unit = { deltaPx ->
                        splitFraction =
                            (splitFraction + deltaPx / totalPx)
                                .coerceIn(MIN_SPECTRO_PANE_FRACTION, MAX_SPECTRO_PANE_FRACTION)
                    }

                    if (isImmersive) {
                        spectrogramPane(Modifier.fillMaxSize())
                    } else if (splitHorizontally) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            spectrogramPane(Modifier.weight(splitFraction).fillMaxHeight())
                            PaneSplitHandle(isHorizontalSplit = true, onDrag = onSplitDrag)
                            vehicleDataPane(Modifier.weight(1f - splitFraction).fillMaxHeight())
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            spectrogramPane(Modifier.weight(splitFraction).fillMaxWidth())
                            PaneSplitHandle(isHorizontalSplit = false, onDrag = onSplitDrag)
                            vehicleDataPane(Modifier.weight(1f - splitFraction).fillMaxWidth())
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
}

/**
 * Top app bar [V14 UX-D8]: two icon actions instead of two text buttons (one of which was
 * the app's only italic), so the title and the logo get the bar back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    onShowInfo: () -> Unit,
    onShowSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            // [§12, plan 4.4] At font scale 1.3 an unconstrained title wrapped to
            // three lines and ran under the logo; it truncates instead.
            Text(
                text = stringResource(R.string.app_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            Image(
                painter = painterResource(id = R.drawable.logo_vibratec),
                contentDescription = stringResource(R.string.cd_logo_vibratec),
                modifier =
                    Modifier
                        .height(LOGO_HEIGHT)
                        .padding(end = NvhSpacing.xs),
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onShowInfo) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.action_info),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(onClick = onShowSettings) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    )
}

/**
 * Draggable divider between the two panes [V14 UX-D7]: reading the spectrogram and watching
 * RPM want different splits, so the operator chooses.
 */
@Composable
private fun PaneSplitHandle(
    isHorizontalSplit: Boolean,
    onDrag: (Float) -> Unit,
) {
    val splitterLabel = stringResource(R.string.cd_pane_splitter)
    Box(
        modifier =
            Modifier
                .then(
                    if (isHorizontalSplit) {
                        Modifier.fillMaxHeight().width(SPLIT_HANDLE_THICKNESS)
                    } else {
                        Modifier.fillMaxWidth().height(SPLIT_HANDLE_THICKNESS)
                    },
                ).draggable(
                    orientation = if (isHorizontalSplit) Orientation.Horizontal else Orientation.Vertical,
                    state = rememberDraggableState(onDelta = onDrag),
                ).semantics { contentDescription = splitterLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .then(
                        if (isHorizontalSplit) {
                            Modifier.width(SPLIT_GRIP_THICKNESS).height(SPLIT_GRIP_LENGTH)
                        } else {
                            Modifier.width(SPLIT_GRIP_LENGTH).height(SPLIT_GRIP_THICKNESS)
                        },
                    ).background(NvhOutline, CircleShape),
        )
    }
}

@Composable
fun GpsLedIndicator(status: GpsStatus) {
    // [§12, plan 4.4] Colour is never the only channel: each state also has its own SHAPE
    // (filled circle / triangle / cross) and its own words. A red-green LED alone is
    // unreadable to a red-green colour-blind operator — and this LED is what tells them
    // whether to trust the RPM numbers. [V14 UX-D10] The shapes are vectors at a fixed dp
    // size, not text glyphs whose weight followed the font scale.
    val (ledColor, textLabel, glyph) =
        when (status) {
            GpsStatus.GOOD -> Triple(NvhStatusGood, stringResource(R.string.gps_signal_good), NvhGlyphShape.DOT)
            GpsStatus.POOR -> Triple(NvhStatusWarn, stringResource(R.string.gps_signal_poor), NvhGlyphShape.TRIANGLE)
            GpsStatus.NONE -> Triple(NvhStatusBad, stringResource(R.string.gps_signal_none), NvhGlyphShape.CROSS)
        }

    val spoken = stringResource(R.string.cd_gps_signal, textLabel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm - NvhSpacing.xxs),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Text(
            text = stringResource(R.string.gps_signal),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        NvhStatusGlyph(shape = glyph, color = ledColor)
        Text(text = textLabel, style = MaterialTheme.typography.labelMedium, color = NvhOnSurfaceVariant)
    }
}

/**
 * Opens the harmonic Emergence Report [A4, plan 4.6, decision D6].
 *
 * Sits in the GMPe banner because that is the context the report describes: it exists only
 * while the kinematic chain is engaged, and its badge shows how many harmonics have been
 * characterised so far. [V14 UX-D11] An `AssistChip` — real ripple bounds, `Role.Button`
 * semantics and the platform-managed 48 dp touch target — instead of a hand-rolled
 * `Surface.clickable` well under the app's own touch floor.
 */
@Composable
fun EmergenceReportButton(
    entryCount: Int,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Outlined.Assignment,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        label = {
            Text(
                text =
                    if (entryCount > 0) {
                        stringResource(R.string.emergence_report_button_count, entryCount)
                    } else {
                        stringResource(R.string.emergence_report_button)
                    },
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = if (entryCount > 0) NvhAccent.copy(alpha = NvhAlpha.TINT) else Color.Transparent,
                labelColor = NvhAccent,
                leadingIconContentColor = NvhAccent,
            ),
        border =
            androidx.compose.foundation.BorderStroke(1.dp, NvhAccent.copy(alpha = NvhAlpha.STRONG)),
    )
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
        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm - NvhSpacing.xxs),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.gps_signal),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        NvhStatusGlyph(shape = NvhGlyphShape.BLOCKED, color = NvhStatusWarn)
        Text(
            text = stringResource(if (coarseOnly) R.string.gps_permission_coarse else R.string.gps_permission_denied),
            style = MaterialTheme.typography.labelMedium,
            color = NvhStatusWarn,
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
        // [V14 UX-M1] Tabular figures: the readout must not jitter as digits change.
        Text(text = value, style = NvhReadoutLarge)
    }
}

/** Crossfades, reveals and colour changes share one duration [V14 UX-M8]. */
private const val MODE_TRANSITION_MS = 220

/** Spectrogram share of the main layout by default; the operator can drag it [UX-D7]. */
private const val DEFAULT_SPECTRO_PANE_FRACTION = 0.55f
private const val MIN_SPECTRO_PANE_FRACTION = 0.35f
private const val MAX_SPECTRO_PANE_FRACTION = 0.75f

/** [V14 UX-M9] Material medium-width class: at 600 dp and above, always split the panes. */
private val MEDIUM_WIDTH_BREAKPOINT = 600.dp

private val SPLIT_HANDLE_THICKNESS = 16.dp
private val SPLIT_GRIP_THICKNESS = 4.dp
private val SPLIT_GRIP_LENGTH = 32.dp

private val LOGO_HEIGHT = 28.dp
private val BANNER_LED_SIZE = 10.dp
private val BANNER_DOT_SIZE = 8.dp
private val NOTICE_ICON_SIZE = 18.dp
private val PROGRESS_SIZE = 32.dp
private val EMPTY_STATE_ICON_SIZE = 32.dp

/** Loaded-file chip label cap, so a long name cannot push the mode chips off-screen. */
private val CHIP_LABEL_MAX_WIDTH = 148.dp

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
