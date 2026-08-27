package com.example.nvhspectro.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.nvhspectro.data.DiagnosticLog
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhSectionContainer
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

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, NvhStatusWarn.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = NvhSectionContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "🩺 Journal de diagnostic",
                color = NvhStatusWarn,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                text =
                    "Les messages d'erreur et d'état de l'application sont enregistrés " +
                        "localement (${formatSize(sizeBytes)}). Aucun envoi automatique : " +
                        "l'application n'a pas d'accès réseau. Vous seul décidez de partager " +
                        "ce fichier.",
                fontSize = 11.sp,
                color = NvhOnSurfaceVariant,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { context.shareDiagnosticLog() },
                    enabled = sizeBytes > 0,
                    modifier = Modifier.semantics { contentDescription = "Partager le journal de diagnostic" },
                ) {
                    Text("Partager", fontSize = 12.sp)
                }
                TextButton(
                    onClick = {
                        DiagnosticLog.clear()
                        sizeBytes = 0
                    },
                    enabled = sizeBytes > 0,
                ) {
                    Text("Effacer", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes <= 0 -> "vide"
        bytes < 1024 -> "$bytes o"
        else -> "${bytes / 1024} ko"
    }

/** Explicit, user-initiated share of the local log — the only way it leaves the device. */
private fun Context.shareDiagnosticLog() {
    val file = DiagnosticLog.currentFile() ?: return
    runCatching {
        val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NVH Spectro — journal de diagnostic")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(send, "Partager le journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { DiagnosticLog.w("Diagnostics", "share failed", it) }
}
