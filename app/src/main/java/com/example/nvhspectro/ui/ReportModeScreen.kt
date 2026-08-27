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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhCanvasChipBorder
import com.example.nvhspectro.theme.NvhEmergenceMarginal
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhModeLive
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhPrimary
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
            title = { Text("Nom de l'ordre", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text("Saisissez la valeur de l'ordre (ex: 29 pour H29) :", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val finalName = if (orderNameInput.isNotBlank()) "Ordre ${orderNameInput.trim()}" else null
                        viewModel.validateCurrentOrder(finalName)
                        showNameDialog = false
                        orderNameInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Valider", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NVH Spectro - Rapport Manuel", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_vibratec),
                        contentDescription = "Logo",
                        modifier = Modifier.height(28.dp).padding(end = 6.dp),
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
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedToggleButton(
                        options = listOf("Absolue", "Émergence"),
                        selectedIndex = if (displayMode == DisplayMode.TTNR) 1 else 0,
                        onOptionSelected = { index ->
                            viewModel.session.setDisplayMode(if (index == 1) DisplayMode.TTNR else DisplayMode.ABSOLUTE)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SegmentedToggleButton(
                        options = listOf("Navigation", "Dessin"),
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
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                    ) {
                        // Toujours afficher "Info GMPe" à gauche, fixe, en italique
                        Text(
                            text = "Info GMPe",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 11.sp,
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
                                        withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) { append("Vh: ") }
                                        withStyle(
                                            style = SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Medium),
                                        ) { append(vhName) }
                                    }
                                val gmpeText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append("GMPe: ") }
                                        withStyle(
                                            style = SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Medium),
                                        ) { append(motorName) }
                                    }
                                val v1000Text =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append("V1000: ") }
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append("$v1000 km/h") }
                                    }

                                AutoResizedText(text = vhText, initialFontSize = 10.sp, minFontSize = 7.sp, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = gmpeText,
                                    initialFontSize = 10.sp,
                                    minFontSize = 7.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = v1000Text,
                                    initialFontSize = 10.sp,
                                    minFontSize = 7.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                val vhText =
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) { append("Vh: ") }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }
                                val gmpeText =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append("GMPe: ") }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }
                                val v1000Text =
                                    buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                                        ) { append("V1000: ") }
                                        withStyle(style = SpanStyle(color = onSurfaceVariantColor)) { append("--") }
                                    }

                                AutoResizedText(text = vhText, initialFontSize = 10.sp, minFontSize = 7.sp, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = gmpeText,
                                    initialFontSize = 10.sp,
                                    minFontSize = 7.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AutoResizedText(
                                    text = v1000Text,
                                    initialFontSize = 10.sp,
                                    minFontSize = 7.sp,
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
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Table Header
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Ordre",
                                modifier = Modifier.weight(1.2f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Vitesse",
                                modifier = Modifier.weight(1.2f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                "Régime",
                                modifier = Modifier.weight(1.3f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                "Fréq",
                                modifier = Modifier.weight(1.2f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), thickness = 1.dp)

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
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1.2f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    val speedText =
                                        if (order.minSpeedKmh != null &&
                                            order.maxSpeedKmh != null
                                        ) {
                                            "${order.minSpeedKmh.toInt()}-${order.maxSpeedKmh.toInt()} km/h"
                                        } else {
                                            "-"
                                        }
                                    Text(
                                        text = speedText,
                                        color = textColor,
                                        fontWeight = fontW,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    val rpmText =
                                        if (order.minRpm != null &&
                                            order.maxRpm != null
                                        ) {
                                            "${order.minRpm}-${order.maxRpm} RpM"
                                        } else {
                                            "-"
                                        }
                                    Text(
                                        text = rpmText,
                                        color = textColor,
                                        fontWeight = fontW,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1.3f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${order.minFreqHz}-${order.maxFreqHz} Hz",
                                        color = textColor,
                                        fontWeight = fontW,
                                        fontSize = 11.sp,
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
                                        text = "Brouillon en cours (${currentUserPoints.size} pts)",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhActiveContainer),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text("Valider ordre", color = NvhOnSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    val brillanceBg = if (isBrillanceModeEnabled) NvhEmergenceMarginal else Color.Transparent
                    val brillanceText = if (isBrillanceModeEnabled) NvhCanvas else NvhOnSurface
                    val brillanceBorder = if (isBrillanceModeEnabled) NvhEmergenceMarginal else NvhCanvasChipBorder

                    Button(
                        onClick = { viewModel.toggleBrillanceMode() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brillanceBg),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, brillanceBorder),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text(
                            "Brillance ordre",
                            color = brillanceText,
                            fontSize = 11.sp,
                            fontWeight = if (isBrillanceModeEnabled) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.clearCurrentPoints() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhExport),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text("Supprime points", color = NvhOnSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { selectedValidatedOrder?.let { viewModel.removeValidatedOrder(it) } },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NvhModeLive),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text("Supprime ordre", color = NvhOnSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                    onClick = { pdfExportLauncher.launch("Rapport_Emergences_NVHSpectro.pdf") },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvhPrimary),
                    border = BorderStroke(1.dp, NvhPrimary),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    Text("Export PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onBack() },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvhStatusBad),
                    border = BorderStroke(1.dp, NvhStatusBad),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    Text("Quitte rapport", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    val cornerRadius = 50.dp
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
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
                            .background(backgroundColor, RoundedCornerShape(cornerRadius))
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
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
    initialFontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    minFontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
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
