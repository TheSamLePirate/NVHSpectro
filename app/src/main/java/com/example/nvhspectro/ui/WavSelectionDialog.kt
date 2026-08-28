package com.example.nvhspectro.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.R
import com.example.nvhspectro.data.RecordingEntry
import com.example.nvhspectro.data.RecordingStore
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhStatusGood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recording picker [plan 1.7]: entries come from RecordingStore (MediaStore on
 * API 29+, legacy folder fallback), listed off the main thread — the old
 * version walked the filesystem inside composition.
 */
@Composable
fun WavSelectionDialog(
    onDismiss: () -> Unit,
    onSelectEntry: (wavUri: Uri, jsonUri: Uri?) -> Unit,
    onImportExternal: () -> Unit,
) {
    val context = LocalContext.current
    // null = still loading
    val entries by produceState<List<RecordingEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { RecordingStore.listRecordings(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.wav_picker_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onImportExternal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.wav_picker_import), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                when {
                    entries == null -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    entries!!.isEmpty() -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.wav_picker_empty, RecordingStore.COLLECTION_DIR),
                                fontSize = 12.sp,
                                color = NvhOnSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.wav_picker_recent, entries!!.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(entries!!) { entry ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectEntry(entry.wavUri, entry.jsonUri)
                                            },
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text =
                                                    stringResource(
                                                        if (entry.jsonUri != null) {
                                                            R.string.wav_entry_with_telemetry
                                                        } else {
                                                            R.string.wav_entry_audio_only
                                                        },
                                                    ),
                                                fontSize = 11.sp,
                                                color = if (entry.jsonUri != null) NvhStatusGood else NvhOnSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = "▶",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
