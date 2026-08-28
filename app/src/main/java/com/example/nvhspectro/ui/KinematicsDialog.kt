package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.nvhspectro.R
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.data.KinematicsInputMode
import com.example.nvhspectro.data.toFlexibleDoubleOrNull
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSpacing

/**
 * GMPe / kinematics configuration [V14 UX-M7]: a `ModalBottomSheet` — this is the app's
 * largest input surface (three input modes, tyre dimensions, target harmonics), which a
 * fixed-height dialog served badly.
 */
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

    NvhSheet(
        title = stringResource(R.string.kin_title),
        onDismiss = onDismiss,
        titleTrailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            // Section Identifiants Véhicule & Moteur
            Text(stringResource(R.string.kin_vehicle_section), style = MaterialTheme.typography.titleSmall)
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE))

            // Mode de Saisie Cinématique
            Text(stringResource(R.string.kin_method_section), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
            ) {
                KinematicsInputMode.values().forEach { mode ->
                    FilterChip(
                        selected = (selectedMode == mode),
                        onClick = { selectedMode = mode },
                        label = {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
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

                    TyreDimensionsFields(
                        tireWidthText = tireWidthText,
                        onTireWidthChange = { tireWidthText = it },
                        tireAspectRatioText = tireAspectRatioText,
                        onTireAspectRatioChange = { tireAspectRatioText = it },
                        rimDiameterText = rimDiameterText,
                        onRimDiameterChange = { rimDiameterText = it },
                        computedRadiusMeters = tempConfig.calculateWheelRadiusMeters(),
                    )
                }
                KinematicsInputMode.DETAILED_CHAIN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm)) {
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

                    TyreDimensionsFields(
                        tireWidthText = tireWidthText,
                        onTireWidthChange = { tireWidthText = it },
                        tireAspectRatioText = tireAspectRatioText,
                        onTireAspectRatioChange = { tireAspectRatioText = it },
                        rimDiameterText = rimDiameterText,
                        onRimDiameterChange = { rimDiameterText = it },
                        computedRadiusMeters = tempConfig.calculateWheelRadiusMeters(),
                    )
                }
            }

            // Carte récapitulative des valeurs calculées
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = NvhAlpha.OUTLINE),
                    ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(NvhSpacing.md), verticalArrangement = Arrangement.spacedBy(NvhSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.kin_summary_section),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.kin_summary_v1000, effectiveV1000),
                        style = NvhReadoutSmall,
                    )
                    Text(
                        text = stringResource(R.string.kin_summary_h1, rpmAt50Kmh, h1At50KmhHz),
                        style = NvhReadoutSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE))

            // Harmoniques Attendues / Liste Blanche
            Text(stringResource(R.string.kin_targets_section), style = MaterialTheme.typography.titleSmall)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targetHarmonicsText.isNotBlank()) NvhAccent else NvhOnSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE))

            // Rémanence visuelle & Commentaires
            Row(horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(NvhSpacing.sm))
                Button(onClick = { onSave(tempConfig) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/**
 * The vendor tyre marking (ex: 205 / 55 R 16), shared by GEAR_RATIO and DETAILED_CHAIN —
 * previously duplicated inline with 9 sp field labels [V14 UX-B2].
 */
@Composable
private fun TyreDimensionsFields(
    tireWidthText: String,
    onTireWidthChange: (String) -> Unit,
    tireAspectRatioText: String,
    onTireAspectRatioChange: (String) -> Unit,
    rimDiameterText: String,
    onRimDiameterChange: (String) -> Unit,
    computedRadiusMeters: Double,
) {
    Text(stringResource(R.string.kin_tyre_section), style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = tireWidthText,
            onValueChange = onTireWidthChange,
            label = { Text(stringResource(R.string.kin_tyre_width), style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1.1f),
        )
        Text("/", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = tireAspectRatioText,
            onValueChange = onTireAspectRatioChange,
            label = { Text(stringResource(R.string.kin_tyre_ratio), style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text("R", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = rimDiameterText,
            onValueChange = onRimDiameterChange,
            label = { Text(stringResource(R.string.kin_tyre_rim), style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = stringResource(R.string.kin_tyre_radius, computedRadiusMeters, 2.0 * Math.PI * computedRadiusMeters),
        style = NvhReadoutSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
