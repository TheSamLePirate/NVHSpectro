package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nvhspectro.R
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.KinematicsInputMode
import com.example.nvhspectro.data.toFlexibleDoubleOrNull
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhOnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinematicsDialog(
    currentConfig: KinematicsConfig,
    onDismiss: () -> Unit,
    onSave: (KinematicsConfig) -> Unit,
) {
    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var selectedMode by remember { mutableStateOf(currentConfig.inputMode) }

    var v1000Text by remember { mutableStateOf(currentConfig.v1000Kmh.toString()) }
    var globalRatioText by remember { mutableStateOf(currentConfig.globalGearRatio.toString()) }
    var reductionRatioText by remember { mutableStateOf(currentConfig.gearReductionRatio.toString()) }
    var axleRatioText by remember { mutableStateOf(currentConfig.axleRatio.toString()) }
    var wheelRadiusText by remember { mutableStateOf(currentConfig.wheelRadiusMeters.toString()) }

    var tireWidthText by remember { mutableStateOf(currentConfig.tireWidthMm.toString()) }
    var tireAspectRatioText by remember { mutableStateOf(currentConfig.tireAspectRatio.toString()) }
    var rimDiameterText by remember { mutableStateOf(currentConfig.rimDiameterInches.toString()) }

    var vehicleName by remember { mutableStateOf(currentConfig.vehicleName) }
    var motorName by remember { mutableStateOf(currentConfig.motorName) }
    var comments by remember { mutableStateOf(currentConfig.comments) }
    var holdTimeText by remember { mutableStateOf(currentConfig.holdTimeSec.toString()) }
    var targetHarmonicsText by remember { mutableStateOf(currentConfig.targetHarmonicsText) }

    // Construction de la configuration temporaire pour calculs en temps réel
    val tempConfig =
        remember(
            isEnabled,
            selectedMode,
            v1000Text,
            globalRatioText,
            reductionRatioText,
            axleRatioText,
            tireWidthText,
            tireAspectRatioText,
            rimDiameterText,
            wheelRadiusText,
            vehicleName,
            motorName,
            comments,
            holdTimeText,
            targetHarmonicsText,
        ) {
            // [C11] Flexible parsing accepts French comma decimals; invalid fields are
            // flagged red below instead of silently reverting to defaults.
            KinematicsConfig(
                isEnabled = isEnabled,
                inputMode = selectedMode,
                v1000Kmh = v1000Text.toFlexibleDoubleOrNull() ?: 10.0,
                globalGearRatio = globalRatioText.toFlexibleDoubleOrNull() ?: 9.5,
                gearReductionRatio = reductionRatioText.toFlexibleDoubleOrNull() ?: 3.2,
                axleRatio = axleRatioText.toFlexibleDoubleOrNull() ?: 3.0,
                tireWidthMm = tireWidthText.trim().toIntOrNull() ?: 205,
                tireAspectRatio = tireAspectRatioText.trim().toIntOrNull() ?: 55,
                rimDiameterInches = rimDiameterText.trim().toIntOrNull() ?: 16,
                wheelRadiusMeters = wheelRadiusText.toFlexibleDoubleOrNull() ?: 0.31,
                vehicleName = vehicleName,
                motorName = motorName,
                comments = comments,
                holdTimeSec = holdTimeText.toFlexibleDoubleOrNull() ?: 3.0,
                targetHarmonicsText = targetHarmonicsText,
            )
        }

    val effectiveV1000 = tempConfig.getEffectiveV1000()
    val h1At50KmhHz = tempConfig.calculateH1FreqHz(50f)
    val rpmAt50Kmh = tempConfig.calculateRpm(50f).toInt()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Titre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.kin_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                    )
                }

                Divider()

                // Section Identifiants Véhicule & Moteur
                Text(stringResource(R.string.kin_vehicle_section), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = vehicleName,
                    onValueChange = { vehicleName = it },
                    label = { Text(stringResource(R.string.kin_vehicle_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = motorName,
                    onValueChange = { motorName = it },
                    label = { Text(stringResource(R.string.kin_motor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Divider()

                // Mode de Saisie Cinématique
                Text(stringResource(R.string.kin_method_section), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KinematicsInputMode.values().forEach { mode ->
                        FilterChip(
                            selected = (selectedMode == mode),
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label, fontSize = 10.5.sp) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Champs de Saisie selon le Mode
                when (selectedMode) {
                    KinematicsInputMode.V1000 -> {
                        OutlinedTextField(
                            value = v1000Text,
                            onValueChange = { v1000Text = it },
                            label = { Text(stringResource(R.string.kin_v1000_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = v1000Text.toFlexibleDoubleOrNull() == null,
                            supportingText = {
                                if (v1000Text.toFlexibleDoubleOrNull() == null) {
                                    Text(stringResource(R.string.kin_invalid_number))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    KinematicsInputMode.GEAR_RATIO -> {
                        OutlinedTextField(
                            value = globalRatioText,
                            onValueChange = { globalRatioText = it },
                            label = { Text(stringResource(R.string.kin_gear_ratio_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = globalRatioText.toFlexibleDoubleOrNull() == null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Dimensions Pneu Vendeur (ex: 205 / 55 R 16)
                        Text(stringResource(R.string.kin_tyre_section), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = tireWidthText,
                                onValueChange = { tireWidthText = it },
                                label = { Text(stringResource(R.string.kin_tyre_width), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1.1f),
                            )
                            Text("/", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = tireAspectRatioText,
                                onValueChange = { tireAspectRatioText = it },
                                label = { Text(stringResource(R.string.kin_tyre_ratio), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Text("R", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = rimDiameterText,
                                onValueChange = { rimDiameterText = it },
                                label = { Text(stringResource(R.string.kin_tyre_rim), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val computedR = tempConfig.calculateWheelRadiusMeters()
                        Text(
                            text = stringResource(R.string.kin_tyre_radius, computedR, 2.0 * Math.PI * computedR),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    KinematicsInputMode.DETAILED_CHAIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reductionRatioText,
                                onValueChange = { reductionRatioText = it },
                                label = { Text(stringResource(R.string.kin_reducer)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                isError = reductionRatioText.toFlexibleDoubleOrNull() == null,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = axleRatioText,
                                onValueChange = { axleRatioText = it },
                                label = { Text(stringResource(R.string.kin_final_drive)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                isError = axleRatioText.toFlexibleDoubleOrNull() == null,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Dimensions Pneu Vendeur (ex: 205 / 55 R 16)
                        Text(stringResource(R.string.kin_tyre_section), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = tireWidthText,
                                onValueChange = { tireWidthText = it },
                                label = { Text(stringResource(R.string.kin_tyre_width), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1.1f),
                            )
                            Text("/", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = tireAspectRatioText,
                                onValueChange = { tireAspectRatioText = it },
                                label = { Text(stringResource(R.string.kin_tyre_ratio), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Text("R", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = rimDiameterText,
                                onValueChange = { rimDiameterText = it },
                                label = { Text(stringResource(R.string.kin_tyre_rim), fontSize = 9.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val computedR = tempConfig.calculateWheelRadiusMeters()
                        Text(
                            text = stringResource(R.string.kin_tyre_radius, computedR, 2.0 * Math.PI * computedR),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Carte récapitulative des valeurs calculées
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.kin_summary_section),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = stringResource(R.string.kin_summary_v1000, effectiveV1000),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = stringResource(R.string.kin_summary_h1, rpmAt50Kmh, h1At50KmhHz),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Divider()

                // Harmoniques Attendues / Liste Blanche
                Text(stringResource(R.string.kin_targets_section), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = targetHarmonicsText,
                    onValueChange = { targetHarmonicsText = it },
                    label = { Text(stringResource(R.string.kin_targets_label)) },
                    placeholder = { Text(stringResource(R.string.kin_targets_hint)) },
                    supportingText = {
                        Text(
                            text =
                                if (targetHarmonicsText.isNotBlank()) {
                                    stringResource(R.string.kin_targets_active)
                                } else {
                                    stringResource(R.string.kin_targets_empty)
                                },
                            fontSize = 11.sp,
                            color = if (targetHarmonicsText.isNotBlank()) NvhAccent else NvhOnSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Divider()

                // Rémanence visuelle & Commentaires
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = holdTimeText,
                        onValueChange = { holdTimeText = it },
                        label = { Text(stringResource(R.string.kin_hold_time)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = holdTimeText.toFlexibleDoubleOrNull() == null,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(stringResource(R.string.kin_comments)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(tempConfig) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
