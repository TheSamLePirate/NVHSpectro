package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhEmergenceHigh
import com.example.nvhspectro.theme.NvhEmergenceMarginal
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSpacing

/** Emergence at or above this level is reported as critical in the report table. */
private const val CRITICAL_EMERGENCE_DB = 6.0

/** The sheet keeps most of the screen: this is a table the operator scans. */
private const val SHEET_HEIGHT_FRACTION = 0.85f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergenceReportDialog(
    entries: List<EmergenceReportEntry>,
    kinematicsConfig: KinematicsConfig,
    onDismiss: () -> Unit,
    onClearReport: () -> Unit,
) {
    NvhSheet(
        title = stringResource(R.string.emergence_report_title),
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            // Encadré Synthese GMPe : Informations Véhicule, V1000 & Commentaires Utilisateur
            val effV1000 = kinematicsConfig.getEffectiveV1000()
            val hasVehicleOrMotor = kinematicsConfig.vehicleName.isNotBlank() || kinematicsConfig.motorName.isNotBlank()
            val hasComments = kinematicsConfig.comments.isNotBlank()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NvhAlpha.OUTLINE),
                    ),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier.padding(NvhSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                when {
                                    !hasVehicleOrMotor -> stringResource(R.string.emergence_vehicle_default)
                                    kinematicsConfig.motorName.isNotBlank() ->
                                        stringResource(
                                            R.string.emergence_vehicle_with_motor,
                                            kinematicsConfig.vehicleName.ifBlank {
                                                stringResource(R.string.emergence_vehicle_fallback)
                                            },
                                            kinematicsConfig.motorName,
                                        )
                                    else ->
                                        stringResource(
                                            R.string.emergence_vehicle,
                                            kinematicsConfig.vehicleName.ifBlank {
                                                stringResource(R.string.emergence_vehicle_fallback)
                                            },
                                        )
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Badge V1000
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = NvhExport,
                        ) {
                            Text(
                                text = stringResource(R.string.emergence_v1000_badge, effV1000),
                                style = NvhReadoutSmall,
                                color = NvhOnSurface,
                                modifier = Modifier.padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xxs),
                            )
                        }
                    }

                    if (hasComments) {
                        Spacer(modifier = Modifier.height(NvhSpacing.xxs))
                        Text(
                            text = stringResource(R.string.emergence_comments_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = NvhAccent,
                        )
                        Text(
                            text = kinematicsConfig.comments,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE))

            if (entries.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!kinematicsConfig.isEnabled) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = NvhOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = NvhSpacing.sm),
                        )
                    }
                    Text(
                        text =
                            if (!kinematicsConfig.isEnabled) {
                                stringResource(R.string.emergence_needs_kinematics)
                            } else {
                                stringResource(R.string.emergence_none_detected)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NvhOnSurfaceVariant,
                    )
                }
            } else {
                // En-tête de tableau
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.report_col_order),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(0.9f),
                    )
                    Text(
                        stringResource(R.string.emergence_col_speed),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.3f),
                    )
                    Text(
                        stringResource(R.string.emergence_col_rpm),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.3f),
                    )
                    Text(
                        stringResource(R.string.emergence_col_freq),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.1f),
                    )
                    Text(
                        stringResource(R.string.emergence_col_emergence),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.1f),
                    )
                }

                // Liste scrollable des entrées
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
                ) {
                    items(entries.sortedByDescending { it.maxEmergenceDb }) { item ->
                        EmergenceReportRow(item)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = NvhAlpha.OUTLINE))

            // Boutons d'action : Réinitialiser & Fermer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClearReport,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        text = stringResource(R.string.emergence_reset),
                        maxLines = 1,
                    )
                }
                Button(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_close),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun EmergenceReportRow(entry: EmergenceReportEntry) {
    // Unchanged criticality rule (≥ 6 dB), expressed with the shared severity tokens
    // [plan 4.3]. The full 5-step ramp used by the 2D graph is deliberately NOT adopted
    // here: a theme commit must not move a threshold an operator reads as "critical".
    val isCritical = entry.maxEmergenceDb >= CRITICAL_EMERGENCE_DB
    val badgeBg = if (isCritical) NvhEmergenceHigh else NvhEmergenceMarginal
    // [§12, plan 4.4] Criticality is not encoded in colour alone: a red and an amber badge
    // are the same badge to a red-green colour-blind operator, so the critical one carries a
    // warning mark as well.
    val badgeMark = if (isCritical) "▲ " else ""
    val badgeSpoken =
        stringResource(
            if (isCritical) R.string.cd_emergence_critical else R.string.cd_emergence,
            entry.maxEmergenceDb,
        )

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NvhAlpha.OUTLINE),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ordre H_k avec badge
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.9f),
            ) {
                Text(
                    text = entry.orderName,
                    style = NvhReadoutSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = NvhSpacing.xs, vertical = NvhSpacing.xxs),
                )
            }

            // Plage de vitesse
            Text(
                text = stringResource(R.string.emergence_range_float, entry.minSpeedKmh, entry.maxSpeedKmh),
                style = NvhReadoutSmall,
                modifier = Modifier.weight(1.3f).padding(start = NvhSpacing.xs),
            )

            // Plage de régime RPM
            Text(
                text = stringResource(R.string.emergence_range_int, entry.minRpm, entry.maxRpm),
                style = NvhReadoutSmall,
                modifier = Modifier.weight(1.3f),
            )

            // Plage de fréquence Hz
            Text(
                text = stringResource(R.string.emergence_range_int, entry.minFreqHz, entry.maxFreqHz),
                style = NvhReadoutSmall,
                modifier = Modifier.weight(1.1f),
            )

            // Émergence max TTNR
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = badgeBg,
                modifier = Modifier.weight(1.1f),
            ) {
                Text(
                    text = stringResource(R.string.emergence_badge, badgeMark, entry.maxEmergenceDb),
                    style = NvhReadoutSmall,
                    color = NvhCanvas,
                    modifier =
                        Modifier
                            .padding(horizontal = NvhSpacing.xs, vertical = NvhSpacing.xxs)
                            .semantics {
                                contentDescription = badgeSpoken
                            },
                )
            }
        }
    }
}
