package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.data.AudioFilter
import com.example.nvhspectro.data.FilterType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField

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
    onRemoveFilter: (String) -> Unit = {}
) {
    val sampleRate = sampleRateHz.toDouble()
    val stepSize = fftSize / 2.0
    val dtStepMs = (stepSize / sampleRate) * 1000.0
    val tBlockMs = (fftSize / sampleRate) * 1000.0
    val fps = sampleRate / stepSize
    val dfHz = sampleRate / fftSize

    val scrollState = rememberScrollState()
    var showFilterDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres NVH & DSP", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SECTION 1: DÉTECTEUR DE BALISES CLIGNOTANTES (POINTS 1 ET 4)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1914)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ DÉTECTEUR DE BALISES CLIGNOTANTES",
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = isDetectorEnabled,
                                onCheckedChange = onDetectorEnabledChange
                            )
                        }

                        if (isDetectorEnabled) {
                            Column {
                                Text(
                                    text = "Seuil d'Émergence (TTNR) : ${String.format("%.1f", emergenceThresholdDb)} dB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Slider(
                                    value = emergenceThresholdDb.toFloat(),
                                    onValueChange = { onEmergenceThresholdChange(it.toDouble()) },
                                    valueRange = 2f..10f
                                )
                            }

                            Column {
                                Text(
                                    text = "Porte d'Amplitude Absolue : ${magnitudeGateDbFS.toInt()} dBFS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Slider(
                                    value = magnitudeGateDbFS.toFloat(),
                                    onValueChange = { onMagnitudeGateChange(it.toDouble()) },
                                    valueRange = -100f..-30f
                                )
                            }
                        }
                    }
                }

                // Temps d'affichage
                if (isWavAnalyzerMode) {
                    Column {
                        Text(
                            text = "Temps d'affichage (Auto WAV) : ${String.format("%.1f", wavDurationSec)} s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "💡 En mode Analyseur WAV, la durée d'affichage est automatique et calée sur la durée réelle de l'enregistrement audio (${String.format("%.1f", wavDurationSec)} s).",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                } else {
                    Column {
                        Text("Temps d'affichage (Direct) : ${String.format("%.1f", timeWindowSec)} s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = timeWindowSec.toFloat(),
                            onValueChange = { onTimeWindowChange(it.toDouble()) },
                            valueRange = 3f..30f
                        )
                    }
                }

                // dB Min / Max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Min : ${minDb.toInt()} dBFS", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = minDb.toFloat(),
                            onValueChange = { onMinDbChange(it.toDouble()) },
                            valueRange = -120f..0f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Max : ${maxDb.toInt()} dBFS", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxDb.toFloat(),
                            onValueChange = { onMaxDbChange(it.toDouble()) },
                            valueRange = -40f..50f
                        )
                    }
                }

                // Taille FFT N (Affichage aéré sur 2 lignes)
                if (isWavAnalyzerMode) {
                    Column {
                        Text(
                            text = "Résolution FFT (Auto Mode Vidéo/WAV) : 2048 pts",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64B5F6)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "💡 En mode Vidéo/Analyseur, la taille FFT est automatiquement fixée à 2048 points pour offrir la meilleure précision acoustique NVH (~10,7 Hz) tout en garantissant une réactivité instantanée.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                } else {
                    Column {
                        Text("Résolution FFT (Taille N)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(512, 1024).forEach { size ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = (fftSize == size),
                                        onClick = { onFftSizeChange(size) }
                                    )
                                    Text("$size pts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(2048, 4096).forEach { size ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = (fftSize == size),
                                        onClick = { onFftSizeChange(size) }
                                    )
                                    Text("$size pts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // TABLEAU RÉCAPITULATIF DSP EXPERT
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "INDICATEURS SIGNAL (N = $fftSize)",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 0.5.dp)

                        DspInfoRow("Recouvrement (Overlap)", "50 %")
                        DspInfoRow("Incrément (Pas Δt)", String.format("%.1f ms", dtStepMs))
                        DspInfoRow("Bloc Temporel (1/Δf)", String.format("%.1f ms", tBlockMs))
                        DspInfoRow("Cadence d'affichage", String.format("%.1f trames/s", fps))
                        DspInfoRow("Résolution Fréq (Δf)", String.format("%.1f Hz", dfHz))
                    }
                }
                

                // SECTION FILTRES AUDIO (QUALITÉ AAA)
                if (isWavAnalyzerMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE91E63).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎛️ FILTRES AUDIO DSP",
                                color = Color(0xFFE91E63),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { showFilterDialog = true },
                                enabled = activeFilters.size < 3,
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(if (activeFilters.size < 3) Color(0xFFE91E63) else Color.Gray.copy(alpha=0.5f), RoundedCornerShape(14.dp))
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        
                        if (activeFilters.isEmpty()) {
                            Text("Aucun filtre actif. Le signal brut est analysé.", color = Color.Gray, fontSize = 11.sp, style = MaterialTheme.typography.bodySmall)
                        } else {
                            // Liste des filtres sous forme de chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeFilters.forEach { filter ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(filter.color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .border(1.dp, filter.color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(filter.type.getDisplayName(), color = filter.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            val freqText = when(filter.type) {
                                                FilterType.LOW_PASS -> "Coupe au-dessus de ${filter.maxFreq} Hz"
                                                FilterType.HIGH_PASS -> "Coupe en-dessous de ${filter.minFreq} Hz"
                                                FilterType.BAND_PASS -> "Garde [${filter.minFreq} - ${filter.maxFreq} Hz]"
                                                FilterType.BAND_STOP -> "Coupe [${filter.minFreq} - ${filter.maxFreq} Hz]"
                                            }
                                            Text(freqText, color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        IconButton(
                                            onClick = { onRemoveFilter(filter.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("X", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fréq Min : $minFreq Hz", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = minFreq.toFloat(),
                            onValueChange = { onMinFreqChange(it.toInt()) },
                            valueRange = 0f..(maxFreq - 100).toFloat().coerceAtLeast(100f)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fréq Max : $maxFreq Hz", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxFreq.toFloat(),
                            onValueChange = { onMaxFreqChange(it.toInt()) },
                            valueRange = (minFreq + 100).toFloat().coerceAtMost(22000f)..22050f
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Valider", fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showFilterDialog) {
        AddFilterDialog(
            existingCount = activeFilters.size,
            onDismiss = { showFilterDialog = false },
            onAddFilter = { filter ->
                onAddFilter(filter)
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun DspInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onAddFilter: (AudioFilter) -> Unit
) {
    var selectedType by remember { mutableStateOf(FilterType.LOW_PASS) }
    var minFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }
    
    val assignedColor = remember(existingCount) {
        when (existingCount) {
            0 -> Color(0xFFFFEB3B) // Jaune
            1 -> Color(0xFF00E676) // Vert
            else -> Color(0xFFFF5252) // Rouge
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un Filtre DSP", fontWeight = FontWeight.Bold, color = Color.White) },
        containerColor = Color(0xFF1E293B),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type de filtre :", color = Color.LightGray, fontSize = 12.sp)
                
                // Dropdown or Radio buttons for type
                Column {
                    FilterType.values().forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (type == selectedType),
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E63))
                            )
                            Text(type.getDisplayName(), color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                
                if (selectedType == FilterType.HIGH_PASS || selectedType == FilterType.BAND_PASS || selectedType == FilterType.BAND_STOP) {
                    OutlinedTextField(
                        value = minFreqText,
                        onValueChange = { minFreqText = it },
                        label = { Text("Fréquence Min (Hz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE91E63)
                        )
                    )
                }
                
                if (selectedType == FilterType.LOW_PASS || selectedType == FilterType.BAND_PASS || selectedType == FilterType.BAND_STOP) {
                    OutlinedTextField(
                        value = maxFreqText,
                        onValueChange = { maxFreqText = it },
                        label = { Text("Fréquence Max (Hz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE91E63)
                        )
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
                    val valid = when(selectedType) {
                        FilterType.LOW_PASS -> max > 0
                        FilterType.HIGH_PASS -> min > 0
                        FilterType.BAND_PASS, FilterType.BAND_STOP -> min > 0 && max > min
                    }
                    
                    if (valid) {
                        onAddFilter(AudioFilter(type = selectedType, minFreq = min, maxFreq = max, color = assignedColor))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Text("Ajouter", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color.LightGray)
            }
        }
    )
}
