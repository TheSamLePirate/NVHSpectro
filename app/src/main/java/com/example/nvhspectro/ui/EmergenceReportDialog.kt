package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nvhspectro.R
import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.data.KinematicsConfig
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhEmergenceHigh
import com.example.nvhspectro.theme.NvhEmergenceMarginal
import com.example.nvhspectro.theme.NvhExport
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant

/** Emergence at or above this level is reported as critical in the report table. */
private const val CRITICAL_EMERGENCE_DB = 6.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergenceReportDialog(
    entries: List<EmergenceReportEntry>,
    kinematicsConfig: KinematicsConfig,
    onDismiss: () -> Unit,
    onClearReport: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(4.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Titre d'en-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.emergence_report_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                // Encadré Synthese GMPe : Informations Véhicule, V1000 & Commentaires Utilisateur
                val effV1000 = kinematicsConfig.getEffectiveV1000()
                val hasVehicleOrMotor = kinematicsConfig.vehicleName.isNotBlank() || kinematicsConfig.motorName.isNotBlank()
                val hasComments = kinematicsConfig.comments.isNotBlank()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Badge V1000
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NvhExport,
                            ) {
                                Text(
                                    text = stringResource(R.string.emergence_v1000_badge, effV1000),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NvhOnSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        if (hasComments) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.emergence_comments_label),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NvhAccent,
                            )
                            Text(
                                text = kinematicsConfig.comments,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style =
                                    androidx.compose.ui.text
                                        .TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                Divider()

                if (entries.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                if (!kinematicsConfig.isEnabled) {
                                    stringResource(R.string.emergence_needs_kinematics)
                                } else {
                                    stringResource(R.string.emergence_none_detected)
                                },
                            fontSize = 13.sp,
                            color = NvhOnSurfaceVariant,
                        )
                    }
                } else {
                    // En-tête de tableau
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.kpi_order, "").trim(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(0.9f),
                        )
                        Text(
                            stringResource(R.string.emergence_col_speed),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.3f),
                        )
                        Text(
                            stringResource(R.string.emergence_col_rpm),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.3f),
                        )
                        Text(
                            stringResource(R.string.emergence_col_freq),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.1f),
                        )
                        Text(
                            stringResource(R.string.emergence_col_emergence),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.1f),
                        )
                    }

                    // Liste scrollable des entrées
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(entries.sortedByDescending { it.maxEmergenceDb }) { item ->
                            EmergenceReportRow(item)
                        }
                    }
                }

                Divider()

                // Boutons d'action : Réinitialiser & Fermer (Formatage AAA strict 1 ligne sans retour à la ligne)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onClearReport,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(
                            text = stringResource(R.string.emergence_reset),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ordre H_k avec badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.9f),
            ) {
                Text(
                    text = entry.orderName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            // Plage de vitesse
            Text(
                text = stringResource(R.string.emergence_range_float, entry.minSpeedKmh, entry.maxSpeedKmh),
                fontSize = 11.sp,
                modifier = Modifier.weight(1.3f),
            )

            // Plage de régime RPM
            Text(
                text = stringResource(R.string.emergence_range_int, entry.minRpm, entry.maxRpm),
                fontSize = 11.sp,
                modifier = Modifier.weight(1.3f),
            )

            // Plage de fréquence Hz
            Text(
                text = stringResource(R.string.emergence_range_int, entry.minFreqHz, entry.maxFreqHz),
                fontSize = 11.sp,
                modifier = Modifier.weight(1.1f),
            )

            // Émergence max TTNR
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeBg,
                modifier = Modifier.weight(1.1f),
            ) {
                Text(
                    text = stringResource(R.string.emergence_badge, badgeMark, entry.maxEmergenceDb),
                    fontWeight = FontWeight.Bold,
                    color = NvhCanvas,
                    fontSize = 10.sp,
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .semantics {
                                contentDescription = badgeSpoken
                            },
                )
            }
        }
    }
}
