import os

file_path = r'app\src\main\java\com\example\nvhspectro\ui\SettingsDialog.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    code = f.read()

# Add imports for UI components
code = code.replace(
    'import androidx.compose.ui.unit.sp',
    'import androidx.compose.ui.unit.sp\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.Close\nimport com.example.nvhspectro.data.AudioFilter\nimport com.example.nvhspectro.data.FilterType\nimport androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.ui.text.input.KeyboardType\nimport androidx.compose.material3.OutlinedTextField'
)

# Add parameters to SettingsDialog
old_params = '''    isWavAnalyzerMode: Boolean = false,
    wavDurationSec: Double = 0.0
) {'''
new_params = '''    isWavAnalyzerMode: Boolean = false,
    wavDurationSec: Double = 0.0,
    activeFilters: List<AudioFilter> = emptyList(),
    onAddFilter: (AudioFilter) -> Unit = {},
    onRemoveFilter: (String) -> Unit = {}
) {'''
code = code.replace(old_params, new_params)

# Add showFilterDialog state
code = code.replace(
    'val scrollState = rememberScrollState()',
    'val scrollState = rememberScrollState()\n    var showFilterDialog by remember { mutableStateOf(false) }'
)

# Add Filters UI section below the "Plage de Fréquences" section
filters_ui = '''
                // SECTION FILTRES AUDIO (QUALITÉ AAA)
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
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFE91E63), RoundedCornerShape(14.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter un filtre", tint = Color.White, modifier = Modifier.size(16.dp))
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
                                                FilterType.LOW_PASS -> "Coupe au-dessus de  Hz"
                                                FilterType.HIGH_PASS -> "Coupe en-dessous de  Hz"
                                                FilterType.BAND_PASS -> "Garde [ -  Hz]"
                                                FilterType.BAND_STOP -> "Coupe [ -  Hz]"
                                            }
                                            Text(freqText, color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        IconButton(
                                            onClick = { onRemoveFilter(filter.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
'''

code = code.replace(
    '                // Plage de Fréquences',
    filters_ui + '\n                // Plage de Fréquences'
)

# Add AddFilterDialog Composable call at the bottom of the SettingsDialog
dialog_call = '''
    if (showFilterDialog) {
        AddFilterDialog(
            onDismiss = { showFilterDialog = false },
            onAddFilter = { filter ->
                onAddFilter(filter)
                showFilterDialog = false
            }
        )
    }
'''
code = code.replace(
    '        }\n    )\n}\n\n@Composable',
    '        }\n    )\n' + dialog_call + '}\n\n@Composable'
)


# Add AddFilterDialog Composable
add_filter_composable = '''
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAddFilter: (AudioFilter) -> Unit
) {
    var selectedType by remember { mutableStateOf(FilterType.LOW_PASS) }
    var minFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }
    
    val colors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFF69F0AE), Color(0xFFFFE082), Color(0xFFE040FB))
    val randomColor = remember { colors.random() }

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
                        onAddFilter(AudioFilter(type = selectedType, minFreq = min, maxFreq = max, color = randomColor))
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
'''
code = code + '\n' + add_filter_composable

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(code)

print("Updated SettingsDialog.kt")
