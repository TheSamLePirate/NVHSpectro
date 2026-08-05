package com.example.nvhspectro.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class ExportedWavEntry(
    val folderName: String,
    val wavFile: File,
    val jsonFile: File?,
    val displayDate: String
)

@Composable
fun WavSelectionDialog(
    onDismiss: () -> Unit,
    onSelectEntry: (File, File?) -> Unit,
    onImportExternal: () -> Unit
) {
    val exportEntries = remember {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportsParent = File(downloadsDir, "NVH_Spectro_Exports")
        val list = mutableListOf<ExportedWavEntry>()
        
        if (exportsParent.exists() && exportsParent.isDirectory) {
            exportsParent.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val wavFile = dir.listFiles()?.firstOrNull { it.name.endsWith(".wav", ignoreCase = true) }
                if (wavFile != null && wavFile.exists()) {
                    val jsonFile = dir.listFiles()?.firstOrNull { it.name.endsWith(".json", ignoreCase = true) }
                    list.add(
                        ExportedWavEntry(
                            folderName = dir.name,
                            wavFile = wavFile,
                            jsonFile = jsonFile,
                            displayDate = dir.name.substringAfterLast("_")
                        )
                    )
                }
            }
        }
        list.sortedByDescending { it.wavFile.lastModified() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📂 Choisir un fichier WAV", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bouton d'importation externe
                OutlinedButton(
                    onClick = onImportExternal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("📥 Importer un fichier WAV extérieur...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                if (exportEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucun enregistrement trouvé dans NVH_Spectro_Exports",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Text(
                        "Enregistrements récents (${exportEntries.size}) :",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(exportEntries) { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectEntry(entry.wavFile, entry.jsonFile)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.folderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (entry.jsonFile != null) "🎵 Audio + 📊 Télémétrie GPS" else "🎵 Audio Seul",
                                            fontSize = 11.sp,
                                            color = if (entry.jsonFile != null) Color(0xFF00E676) else Color.Gray
                                        )
                                    }
                                    Text(
                                        text = "▶",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}
