package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.R

@Composable
fun SaveRecordingDialog(
    durationSec: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.save_recording_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.save_recording_done, durationSec),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = customName,
                    onValueChange = { input ->
                        if (input.length <= MAX_NAME_LENGTH) {
                            customName = input
                        }
                    },
                    label = { Text(stringResource(R.string.save_recording_name_label, MAX_NAME_LENGTH)) },
                    placeholder = { Text(stringResource(R.string.save_recording_name_hint)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = stringResource(R.string.save_recording_counter, customName.length, MAX_NAME_LENGTH),
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                if (customName.length == MAX_NAME_LENGTH) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text =
                        stringResource(
                            R.string.save_recording_folder,
                            customName.ifBlank { DEFAULT_RECORDING_NAME },
                        ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(customName) },
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Recording-name limit, enforced here and by the save path [S5]. */
private const val MAX_NAME_LENGTH = 20

private const val DEFAULT_RECORDING_NAME = "Essai"
