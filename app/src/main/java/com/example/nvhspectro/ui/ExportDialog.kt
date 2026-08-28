package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.TelemetryData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    telemetry: TelemetryData,
    onExport: (String, String) -> Unit,
) {
    var pedalPercent by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.export_current_speed, telemetry.speedKmh))

                OutlinedTextField(
                    value = pedalPercent,
                    onValueChange = { pedalPercent = it },
                    label = { Text(stringResource(R.string.export_pedal)) },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(stringResource(R.string.export_comments)) },
                    modifier = Modifier.height(100.dp),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(pedalPercent, comments) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
