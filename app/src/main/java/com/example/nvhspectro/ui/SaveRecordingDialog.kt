package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SaveRecordingDialog(
    durationSec: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sauvegarder l'Essai Audio & GPS", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enregistrement de $durationSec seconde(s) terminé.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = customName,
                    onValueChange = { input ->
                        if (input.length <= 20) {
                            customName = input
                        }
                    },
                    label = { Text("Nom de l'essai (max 20 car.)") },
                    placeholder = { Text("Ex: TestMoteur") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${customName.length} / 20 caractères",
                            modifier = Modifier.fillMaxWidth(),
                            color = if (customName.length == 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Le dossier sera nommé : ${if (customName.isBlank()) "Essai" else customName}_[Date_Heure]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(customName) }
            ) {
                Text("Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
