package com.example.nvhspectro.ui

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nvhspectro.AudioConfig
import com.example.nvhspectro.DisplayMode
import com.example.nvhspectro.ReportViewModel
import com.example.nvhspectro.R
import com.example.nvhspectro.SpectrogramCanvas
import com.example.nvhspectro.theme.NvhActiveContainer
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhCanvasChipBorder
import com.example.nvhspectro.theme.NvhEmergenceMarginal
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhModeLive
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhMinTouchTarget
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhPrimary
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhStatusBad
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportModeScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit,
) {
    val reportFftHistory by viewModel.reportFftHistory.collectAsStateWithLifecycle()
    val reportAbsHistory by viewModel.reportFftHistoryAbsolute.collectAsStateWithLifecycle()
    val reportTtnrHistory by viewModel.reportFftHistoryTTNR.collectAsStateWithLifecycle()

    val displayMode by viewModel.session.displayMode.collectAsStateWithLifecycle()
    val minFreq by viewModel.session.minFreq.collectAsStateWithLifecycle()
    val maxFreq by viewModel.session.maxFreq.collectAsStateWithLifecycle()
    val minDb by viewModel.session.minDb.collectAsStateWithLifecycle()
    val maxDb by viewModel.session.maxDb.collectAsStateWithLifecycle()

    val manualTrackedOrders by viewModel.manualTrackedOrders.collectAsStateWithLifecycle()
    val selectedValidatedOrder by viewModel.selectedValidatedOrder.collectAsStateWithLifecycle()
    val isBrillanceModeEnabled by viewModel.isBrillanceModeEnabled.collectAsStateWithLifecycle()
    val kinematicsConfig by viewModel.session.kinematicsConfig.collectAsStateWithLifecycle()
    val currentUserPoints by viewModel.currentUserPoints.collectAsStateWithLifecycle()
    val currentSmartPath by viewModel.currentSmartPath.collectAsStateWithLifecycle()

    // [C1] Report mode can hold a snapshot from live capture or from a loaded file.
    val loadedWavData by viewModel.session.loadedWavData.collectAsStateWithLifecycle()
    val sampleRate = loadedWavData?.sampleRate ?: AudioConfig.LIVE_SAMPLE_RATE_HZ
    val context = LocalContext.current

    var isDrawingMode by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var orderNameInput by remember { mutableStateOf("") }
    val pdfFileName = stringResource(R.string.report_pdf_filename)
    val orderNamePrefix = stringResource(R.string.report_order_name_prefix, "%s")

    val pdfExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf"),
            onResult = { uri ->
                uri?.let { viewModel.savePdfToUri(context, it) }
            },
        )

    // Dialog pour nommer l'ordre
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.report_order_name_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(stringResource(R.string.report_order_name_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = orderNameInput,
                        onValueChange = { orderNameInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = NvhOutline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = if (orderNameInput.isNotBlank()) orderNamePrefix.format(orderNameInput.trim()) else null
                        viewModel.validateCurrentOrder(finalName)
                        showNameDialog = false
                        orderNameInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.action_validate), color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.report_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_vibratec),
                        contentDescription = stringResource(R.string.cd_logo_vibratec),
                        modifier = Modifier.height(28.dp).padding(end = NvhSpacing.sm),
                        contentScale = ContentScale.Fit,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            // --- 1. TOGGLES AND INFO ---
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
            ) {
                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedToggleButton(
                        options = listOf(stringResource(R.string.report_mode_absolute), stringResource(R.string.report_mode_emergence)),
                        selectedIndex = if (displayMode == DisplayMode.TTNR) 1 else 0,
                        onOptionSelected = { index ->
                            viewModel.session.setDisplayMode(if (index == 1) DisplayMode.TTNR else DisplayMode.ABSOLUTE)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SegmentedToggleButton(
                        options = listOf(stringResource(R.string.report_mode_navigation), stringResource(R.string.report_mode_drawing)),
                        selectedIndex = if (isDrawingMode) 1 else 0,
                        onOptionSelected = { index ->
                            isDrawingMode = (index == 1)
                        },
                        modifier = Modifier.weight(1f),
                        activeColor = MaterialTheme.colorScheme.secondary,
                    )
                }

                // GMPe Info Box
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                    ) {
                        // Toujours afficher stringResource(R.string.report_gmpe_info) à gauche, fixe, en italique
                        Text(
                            text = stringResource(R.string.report_gmpe_info),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Les infos dynamiques à droite
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 4.dp), // marge à droite
                        ) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                            val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

                            if (kinematicsConfig.isEnabled) {
                                val vhName = kinematicsConfig.vehicleName.ifEmpty { "--" }
                                val motorName = kinematicsConfig.motorName.ifEmpty { "--" }
                                // [U7, plan 4.5] The EFFECTIVE V1000 — the value the RPM and
                                // order maths actually use. The report header showed the raw
                                // `v1000Kmh` field, which the calculation ignores entirely in
                                // GEAR_RATIO / DETAILED_CHAIN mode: the operator read one
                                // number while the instrument used another.
                                val v1000 = String.format(Locale.FRANCE, "%.1f", kinematicsConfig.getEffectiveV1000())

                                val vhText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_vehicle_prefix)) }
                                        withStyle(
                                            style = SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Medium),
                                        ) { append(vhName) }
                                    }
                                val gmpeText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_motor_prefix)) }
                                        withStyle(
                                            style = SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Medium),
                                        ) { append(motorName) }
                                    }
                                val v1000Text =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_v1000_prefix)) }
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_v1000_value, v1000)) }
                                    }

                                AutoResizedText(text = vhText, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = gmpeText,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = v1000Text,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                val vhText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_vehicle_prefix)) }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }
                                val gmpeText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_motor_prefix)) }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }
                                val v1000Text =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append(stringResource(R.string.report_v1000_prefix)) }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }

                                AutoResizedText(text = vhText, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = gmpeText,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = v1000Text,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. SPECTROGRAM (same size as direct mode) ---
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.50f)
                        .background(NvhCanvas),
                contentAlignment = Alignment.Center,
            ) {
                SpectrogramCanvas(
                    history = if (displayMode == DisplayMode.TTNR) reportTtnrHistory else reportFftHistory,
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
                    isWavAnalyzerMode = false,
                    isReportModeActive = true,
                    isDrawingMode = isDrawingMode,
                    currentUserPoints = currentUserPoints,
                    manualTrackedOrders = manualTrackedOrders,
                    selectedManualOrder = selectedValidatedOrder,
                    currentSmartPath = currentSmartPath,
                    isBrillanceModeEnabled = isBrillanceModeEnabled,
                    onAddManualPoint = { frameIdx: Int, binIdx: Int ->
                        viewModel.addManualTrackPoint(frameIdx, binIdx)
                    },
                )
            }

            // --- 3. LIST OF ORDERS ---
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.20f)
                        .padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NvhAlpha.OUTLINE),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NvhAlpha.FAINT)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Table Header
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.report_col_order),
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.report_col_speed),
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.report_col_rpm),
                                modifier = Modifier.weight(1.3f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.report_col_freq),
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NvhAlpha.FAINT), thickness = 1.dp)

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
                        itemsIndexed(manualTrackedOrders) { index, order ->
                            val isSelected = selectedValidatedOrder == order
                            val rowColor =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (index % 2 == 0) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                                }

                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                viewModel.selectValidatedOrder(null)
                                            } else {
                                                viewModel.selectValidatedOrder(order)
                                            }
                                        },
                                color = rowColor,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    val fontW = if (isSelected) FontWeight.Bold else FontWeight.Normal

                                    Text(
                                        text = order.name,
                                        color = textColor,
                                        fontWeight = fontW,
                                        style = NvhReadoutSmall,
                                        modifier = Modifier.weight(1.2f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    val speedText =
                                        if (order.minSpeedKmh != null &&
                                            order.maxSpeedKmh != null
                                        ) {
                                            stringResource(
                                                R.string.report_speed_range,
                                                order.minSpeedKmh.toInt(),
                                                order.maxSpeedKmh.toInt(),
                                            )
                                        } else {
                                            "-"
                                        }
                                    Text(
                                        text = speedText,
                                        color = textColor,
                                        fontWeight = fontW,
                                        style = NvhReadoutSmall,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    val rpmText =
                                        if (order.minRpm != null &&
                                            order.maxRpm != null
                                        ) {
                                            stringResource(R.string.report_rpm_range, order.minRpm ?: 0, order.maxRpm ?: 0)
                                        } else {
                                            "-"
                                        }
                                    Text(
                                        text = rpmText,
                                        color = textColor,
                                        fontWeight = fontW,
                                        style = NvhReadoutSmall,
                                        modifier = Modifier.weight(1.3f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.report_freq_range, order.minFreqHz, order.maxFreqHz),
                                        color = textColor,
                                        fontWeight = fontW,
                                        style = NvhReadoutSmall,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (currentUserPoints.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.report_draft, currentUserPoints.size),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.sm),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. ACTION BUTTONS ---
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showNameDialog = true },
                        modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhActiveContainer),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.report_validate_order),
                            color = NvhOnSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }

                    val brillanceBg = if (isBrillanceModeEnabled) NvhEmergenceMarginal else Color.Transparent
                    val brillanceText = if (isBrillanceModeEnabled) NvhCanvas else NvhOnSurface
                    val brillanceBorder = if (isBrillanceModeEnabled) NvhEmergenceMarginal else NvhCanvasChipBorder

                    Button(
                        onClick = { viewModel.toggleBrillanceMode() },
                        modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = brillanceBg),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, brillanceBorder),
                        contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.report_brightness),
                            color = brillanceText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isBrillanceModeEnabled) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.clearCurrentPoints() },
                        modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhExport),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.report_clear_points),
                            color = NvhOnSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }

                    Button(
                        onClick = { selectedValidatedOrder?.let { viewModel.removeValidatedOrder(it) } },
                        modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhModeLive),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                    ) {
                        Text(
                            stringResource(R.string.report_remove_order),
                            color = NvhOnSurface,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            // --- 5. EXPORT & QUIT ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pdfExportLauncher.launch(pdfFileName) },
                    modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvhPrimary),
                    border = BorderStroke(1.dp, NvhPrimary),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                ) {
                    Text(stringResource(R.string.report_export_pdf), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { onBack() },
                    modifier = Modifier.weight(1f).height(NvhMinTouchTarget),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvhStatusBad),
                    border = BorderStroke(1.dp, NvhStatusBad),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                ) {
                    Text(stringResource(R.string.report_quit), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SegmentedToggleButton(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.height(NvhMinTouchTarget),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NvhAlpha.OUTLINE),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = NvhAlpha.FAINT)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, text ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(300),
                    label = "SegmentedBgColor",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) NvhOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300),
                    label = "SegmentedTextColor",
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(backgroundColor, CircleShape)
                            .clickable(
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                                indication = null,
                            ) {
                                onOptionSelected(index)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AutoResizedText(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    initialFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
) {
    var fontSize by remember(text) { mutableStateOf(initialFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        fontSize = fontSize,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier.alpha(if (readyToDraw) 1f else 0f),
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && fontSize.value > minFontSize.value) {
                val nextSize = fontSize.value * 0.9f
                if (nextSize < minFontSize.value) {
                    fontSize = minFontSize
                } else {
                    fontSize = nextSize.sp
                }
            } else {
                readyToDraw = true
            }
        },
    )
}

