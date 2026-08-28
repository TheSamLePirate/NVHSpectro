package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.AudioConfig
import com.example.nvhspectro.R
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.FilterType
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhDetectorAccent
import com.example.nvhspectro.theme.NvhFilter
import com.example.nvhspectro.theme.NvhFilterAccent
import com.example.nvhspectro.theme.NvhMinTouchTarget
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhOrderTrace
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhSurfaceVariant
import com.example.nvhspectro.theme.NvhTheoretical
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * Settings surface [V14 UX-M7]: a `ModalBottomSheet`, not a scrolling `AlertDialog` — the
 * previous construction capped a 571-line settings UI at ~60% screen height with its own
 * scrollbar inside a dialog inside a scrim. Sliders apply live; dismissing keeps them.
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    sampleRateHz: Int,
    minDb: Double,
    maxDb: Double,
    onMinDbChange: (Double) -> Unit,
    onMaxDbChange: (Double) -> Unit,
    fftSize: Int,
    onFftSizeChange: (Int) -> Unit,
    minFreq: Int = 0,
    onMinFreqChange: (Int) -> Unit = {},
    maxFreq: Int,
    onMaxFreqChange: (Int) -> Unit = {},
    timeWindowSec: Double,
    onTimeWindowChange: (Double) -> Unit,
    isDetectorEnabled: Boolean = true,
    onDetectorEnabledChange: (Boolean) -> Unit = {},
    emergenceThresholdDb: Double = 2.5,
    onEmergenceThresholdChange: (Double) -> Unit = {},
    magnitudeGateDbFS: Double = -90.0,
    onMagnitudeGateChange: (Double) -> Unit = {},
    isWavAnalyzerMode: Boolean = false,
    wavDurationSec: Double = 0.0,
    activeFilters: List<AudioFilter> = emptyList(),
    onAddFilter: (AudioFilter) -> Unit = {},
    onRemoveFilter: (String) -> Unit = {},
) {
    val sampleRate = sampleRateHz.toDouble()
    val stepSize = fftSize / 2.0
    val dtStepMs = (stepSize / sampleRate) * 1000.0
    val tBlockMs = (fftSize / sampleRate) * 1000.0
    val fps = sampleRate / stepSize
    val dfHz = sampleRate / fftSize

    var showFilterDialog by remember { mutableStateOf(false) }
    val addFilterLabel = stringResource(R.string.cd_add_filter)

    NvhSheet(
        title = stringResource(R.string.settings_title),
        onDismiss = onDismiss,
        titleTrailing = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_validate))
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            // SECTION 1: DÉTECTEUR DE BALISES CLIGNOTANTES
            NvhSection(
                title = stringResource(R.string.settings_detector_title),
                accent = NvhDetectorAccent,
                trailing = {
                    Switch(
                        checked = isDetectorEnabled,
                        onCheckedChange = onDetectorEnabledChange,
                    )
                },
            ) {
                if (isDetectorEnabled) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_emergence_threshold, emergenceThresholdDb),
                            style = MaterialTheme.typography.bodySmall,
                            color = NvhOnSurface,
                        )
                        Slider(
                            value = emergenceThresholdDb.toFloat(),
                            onValueChange = { onEmergenceThresholdChange(it.toDouble()) },
                            valueRange = 2f..10f,
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.settings_magnitude_gate, magnitudeGateDbFS.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = NvhOnSurface,
                        )
                        Slider(
                            value = magnitudeGateDbFS.toFloat(),
                            onValueChange = { onMagnitudeGateChange(it.toDouble()) },
                            valueRange = -100f..-30f,
                        )
                    }
                }
            }

            // Temps d'affichage
            if (isWavAnalyzerMode) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_time_window_wav, wavDurationSec),
                        style = MaterialTheme.typography.titleSmall,
                        color = NvhModeWavAccent,
                    )
                    Text(
                        text = stringResource(R.string.settings_time_window_wav_help, wavDurationSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = NvhOnSurfaceVariant,
                    )
                }
            } else {
                Column {
                    Text(
                        stringResource(R.string.settings_time_window_live, timeWindowSec),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = timeWindowSec.toFloat(),
                        onValueChange = { onTimeWindowChange(it.toDouble()) },
                        valueRange = 3f..30f,
                    )
                }
            }

            // dB Min / Max
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvhSpacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_min_db, minDb.toInt()), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = minDb.toFloat(),
                        onValueChange = { onMinDbChange(it.toDouble()) },
                        valueRange = -120f..0f,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_max_db, maxDb.toInt()), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = maxDb.toFloat(),
                        onValueChange = { onMaxDbChange(it.toDouble()) },
                        valueRange = -40f..50f,
                    )
                }
            }

            // Taille FFT N
            if (isWavAnalyzerMode) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_fft_auto, AudioConfig.WAV_FFT_SIZE),
                        style = MaterialTheme.typography.titleSmall,
                        color = NvhTheoretical,
                    )
                    Spacer(modifier = Modifier.height(NvhSpacing.xxs))
                    Text(
                        text = stringResource(R.string.settings_fft_auto_help, AudioConfig.WAV_FFT_SIZE),
                        style = MaterialTheme.typography.bodySmall,
                        color = NvhOnSurfaceVariant,
                    )
                }
            } else {
                Column {
                    Text(
                        stringResource(R.string.settings_fft_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(NvhSpacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        listOf(512, 1024).forEach { size ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (fftSize == size),
                                    onClick = { onFftSizeChange(size) },
                                )
                                Text(
                                    stringResource(R.string.settings_fft_points, size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        listOf(2048, 4096).forEach { size ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (fftSize == size),
                                    onClick = { onFftSizeChange(size) },
                                )
                                Text(
                                    stringResource(R.string.settings_fft_points, size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            // TABLEAU RÉCAPITULATIF DSP EXPERT
            NvhSection(
                title = stringResource(R.string.settings_dsp_title, fftSize),
                accent = NvhAccent,
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE), thickness = 1.dp)

                DspInfoRow(stringResource(R.string.settings_dsp_overlap), stringResource(R.string.settings_dsp_overlap_value))
                DspInfoRow(stringResource(R.string.settings_dsp_step), stringResource(R.string.settings_value_ms, dtStepMs))
                DspInfoRow(stringResource(R.string.settings_dsp_block), stringResource(R.string.settings_value_ms, tBlockMs))
                DspInfoRow(stringResource(R.string.settings_dsp_fps), stringResource(R.string.settings_value_fps, fps))
                DspInfoRow(stringResource(R.string.settings_dsp_df), stringResource(R.string.settings_value_hz, dfHz))
            }

            // SECTION FILTRES AUDIO
            if (isWavAnalyzerMode) {
                NvhSection(
                    title = stringResource(R.string.settings_filters_title),
                    accent = NvhFilterAccent,
                    trailing = {
                        FilledIconButton(
                            onClick = { showFilterDialog = true },
                            enabled = activeFilters.size < MAX_FILTERS,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = NvhFilter),
                            modifier =
                                Modifier
                                    // [§12, plan 4.4] 48 dp target with a spoken label:
                                    // "+" alone tells TalkBack nothing.
                                    .size(NvhMinTouchTarget)
                                    .semantics { contentDescription = addFilterLabel },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = NvhOnSurface)
                        }
                    },
                ) {
                    if (activeFilters.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_filters_empty),
                            color = NvhOnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm)) {
                            activeFilters.forEach { filter ->
                                val removeLabel = stringResource(R.string.cd_remove_filter, filter.type.getDisplayName())
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                filter.color.copy(alpha = NvhAlpha.FAINT),
                                                MaterialTheme.shapes.small,
                                            ).border(
                                                1.dp,
                                                filter.color.copy(alpha = NvhAlpha.OUTLINE),
                                                MaterialTheme.shapes.small,
                                            ).padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            filter.type.getDisplayName(),
                                            color = filter.color,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        val freqText =
                                            when (filter.type) {
                                                FilterType.LOW_PASS -> stringResource(R.string.filter_low_pass_desc, filter.maxFreq)
                                                FilterType.HIGH_PASS ->
                                                    stringResource(
                                                        R.string.filter_high_pass_desc,
                                                        filter.minFreq,
                                                    )
                                                FilterType.BAND_PASS ->
                                                    stringResource(
                                                        R.string.filter_band_pass_desc,
                                                        filter.minFreq,
                                                        filter.maxFreq,
                                                    )
                                                FilterType.BAND_STOP ->
                                                    stringResource(
                                                        R.string.filter_band_stop_desc,
                                                        filter.minFreq,
                                                        filter.maxFreq,
                                                    )
                                            }
                                        Text(freqText, color = NvhOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(
                                        onClick = { onRemoveFilter(filter.id) },
                                        modifier =
                                            Modifier
                                                .size(NvhMinTouchTarget)
                                                .semantics {
                                                    contentDescription = removeLabel
                                                },
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = null, tint = NvhOnSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            } // Fin if (isWavAnalyzerMode)

            // Plage de Fréquences d'analyse (Min & Max)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvhSpacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_freq_min, minFreq), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = minFreq.toFloat(),
                        onValueChange = { onMinFreqChange(it.toInt()) },
                        valueRange = 0f..(maxFreq - 100).toFloat().coerceAtLeast(100f),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_freq_max, maxFreq), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = maxFreq.toFloat(),
                        onValueChange = { onMaxFreqChange(it.toInt()) },
                        valueRange = (minFreq + 100).toFloat().coerceAtMost(22000f)..22050f,
                    )
                }
            }
        }
    }

    if (showFilterDialog) {
        AddFilterDialog(
            existingCount = activeFilters.size,
            onDismiss = { showFilterDialog = false },
            onAddFilter = { filter ->
                onAddFilter(filter)
                showFilterDialog = false
            },
        )
    }
}

@Composable
fun DspInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = NvhOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        // [V14 UX-M1] Tabular figures: DSP indicators are measurements.
        Text(text = value, color = NvhOnSurface, style = NvhReadoutSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onAddFilter: (AudioFilter) -> Unit,
) {
    var selectedType by remember { mutableStateOf(FilterType.LOW_PASS) }
    var minFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }

    val assignedColor =
        remember(existingCount) {
            when (existingCount) {
                0 -> NvhOrderTrace[4] // ambre
                1 -> NvhOrderTrace[2] // vert
                else -> NvhOrderTrace[6] // rouge
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_dialog_title), color = NvhOnSurface) },
        containerColor = NvhSurfaceVariant,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NvhSpacing.md)) {
                Text(
                    stringResource(R.string.filter_dialog_type),
                    color = NvhOnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                Column {
                    FilterType.values().forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (type == selectedType),
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = NvhFilterAccent),
                            )
                            Text(type.getDisplayName(), color = NvhOnSurface, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (selectedType == FilterType.HIGH_PASS || selectedType == FilterType.BAND_PASS || selectedType == FilterType.BAND_STOP) {
                    OutlinedTextField(
                        value = minFreqText,
                        onValueChange = { minFreqText = it },
                        label = { Text(stringResource(R.string.filter_dialog_min)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NvhOnSurface,
                                unfocusedTextColor = NvhOnSurface,
                                focusedBorderColor = NvhFilterAccent,
                            ),
                    )
                }

                if (selectedType == FilterType.LOW_PASS || selectedType == FilterType.BAND_PASS || selectedType == FilterType.BAND_STOP) {
                    OutlinedTextField(
                        value = maxFreqText,
                        onValueChange = { maxFreqText = it },
                        label = { Text(stringResource(R.string.filter_dialog_max)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NvhOnSurface,
                                unfocusedTextColor = NvhOnSurface,
                                focusedBorderColor = NvhFilterAccent,
                            ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val min = minFreqText.toIntOrNull() ?: 0
                    val max = maxFreqText.toIntOrNull() ?: 20000

                    // Validation
                    val valid =
                        when (selectedType) {
                            FilterType.LOW_PASS -> max > 0
                            FilterType.HIGH_PASS -> min > 0
                            FilterType.BAND_PASS, FilterType.BAND_STOP -> min > 0 && max > min
                        }

                    if (valid) {
                        onAddFilter(AudioFilter(type = selectedType, minFreq = min, maxFreq = max, color = assignedColor))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NvhFilter),
            ) {
                Text(stringResource(R.string.action_add), color = NvhOnSurface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = NvhOnSurfaceVariant)
            }
        },
    )
}

/** DSP filter chain cap [S1]. */
private const val MAX_FILTERS = 3
