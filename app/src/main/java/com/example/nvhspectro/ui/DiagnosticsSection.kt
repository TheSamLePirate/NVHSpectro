package com.example.nvhspectro.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import com.example.nvhspectro.R
import com.example.nvhspectro.data.DiagnosticLog
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhStatusWarn

/**
 * The diagnostic-log surface [V3, plan 4.7].
 *
 * Every notice the app shows an operator is also written to a local, size-bounded file. This
 * is the only way it ever leaves the device: an explicit share, chosen by the user, to an app
 * they pick. There is no network path — the app holds no INTERNET permission and this feature
 * exists precisely so that none is needed to diagnose a field failure.
 */
@Composable
fun DiagnosticsSection() {
    val context = LocalContext.current
    var sizeBytes by remember { mutableLongStateOf(DiagnosticLog.sizeBytes()) }
    val shareLabel = stringResource(R.string.cd_diagnostics_share)

    NvhSection(
        title = stringResource(R.string.diagnostics_title),
        accent = NvhStatusWarn,
    ) {
        Text(
            text = stringResource(R.string.diagnostics_explanation, formatSize(sizeBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = NvhOnSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm)) {
            OutlinedButton(
                onClick = { context.shareDiagnosticLog() },
                enabled = sizeBytes > 0,
                modifier = Modifier.semantics { contentDescription = shareLabel },
            ) {
                Text(stringResource(R.string.diagnostics_share))
            }
            TextButton(
                onClick = {
                    DiagnosticLog.clear()
                    sizeBytes = 0
                },
                enabled = sizeBytes > 0,
            ) {
                Text(stringResource(R.string.diagnostics_clear))
            }
        }
    }
}

@Composable
private fun formatSize(bytes: Long): String =
    when {
        bytes <= 0 -> stringResource(R.string.diagnostics_size_empty)
        bytes < BYTES_PER_KB -> stringResource(R.string.diagnostics_size_bytes, bytes)
        else -> stringResource(R.string.diagnostics_size_kb, bytes / BYTES_PER_KB)
    }

private const val BYTES_PER_KB = 1024L

/** Explicit, user-initiated share of the local log — the only way it leaves the device. */
private fun Context.shareDiagnosticLog() {
    val file = DiagnosticLog.currentFile() ?: return
    runCatching {
        val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(
            Intent.createChooser(send, getString(R.string.diagnostics_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { DiagnosticLog.w("Diagnostics", "share failed", it) }
}
