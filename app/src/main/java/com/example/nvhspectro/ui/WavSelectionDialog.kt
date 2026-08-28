package com.example.nvhspectro.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.data.RecordingEntry
import com.example.nvhspectro.data.RecordingStore
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhSpacing
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
            Text(stringResource(R.string.wav_picker_title))
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = PICKER_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = onImportExternal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.wav_picker_import))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                when {
                    entries == null -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = NvhSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE))
                        }
                    }
                    entries!!.isEmpty() -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = NvhSpacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.wav_picker_empty, RecordingStore.COLLECTION_DIR),
                                style = MaterialTheme.typography.bodySmall,
                                color = NvhOnSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.wav_picker_recent, entries!!.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(entries!!) { entry ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
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
                                                .padding(NvhSpacing.md),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.displayName,
                                                style = MaterialTheme.typography.titleSmall,
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
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (entry.jsonUri != null) NvhStatusGood else NvhOnSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
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

private val PICKER_MAX_HEIGHT = 400.dp
private val PROGRESS_SIZE = 28.dp
