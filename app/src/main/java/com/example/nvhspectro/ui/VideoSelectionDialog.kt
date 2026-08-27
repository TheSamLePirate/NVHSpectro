package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.theme.NvhModeVideo
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhStatusBad

@Composable
fun VideoSelectionDialog(
    onDismiss: () -> Unit,
    onSelectLocalVideo: () -> Unit,
    onSelectYouTubeUrl: (String) -> Unit,
) {
    var showYouTubeInput by remember { mutableStateOf(false) }
    var youtubeUrlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "🎬 Source Vidéo NVH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!showYouTubeInput) {
                    Text(
                        text = "Choisissez la provenance de l'enregistrement vidéo pour l'analyse spectrale synchronisée :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Bouton Fichier Local
                    Button(
                        onClick = {
                            onDismiss()
                            onSelectLocalVideo()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NvhModeVideo,
                                contentColor = NvhOnSurface,
                            ),
                    ) {
                        Text(
                            text = "📂 Fichier Vidéo Local (MP4, MKV, AVI...)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }

                    // Bouton Lien YouTube
                    OutlinedButton(
                        onClick = { showYouTubeInput = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "🌐 Lien / URL YouTube",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NvhStatusBad,
                        )
                    }
                } else {
                    Text(
                        text = "Saisissez ou collez le lien de la vidéo YouTube :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = youtubeUrlText,
                        onValueChange = { youtubeUrlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { showYouTubeInput = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retour")
                        }

                        Button(
                            onClick = {
                                if (youtubeUrlText.isNotBlank()) {
                                    onDismiss()
                                    onSelectYouTubeUrl(youtubeUrlText.trim())
                                }
                            },
                            enabled = youtubeUrlText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = NvhStatusBad,
                                    contentColor = NvhOnSurface,
                                ),
                        ) {
                            Text("Valider")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!showYouTubeInput) {
                TextButton(onClick = onDismiss) {
                    Text("Annuler")
                }
            }
        },
    )
}
