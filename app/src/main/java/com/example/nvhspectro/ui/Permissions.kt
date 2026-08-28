package com.example.nvhspectro.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhSectionContainer
import com.example.nvhspectro.theme.NvhStatusWarn

/**
 * What the app is actually allowed to do right now [U1, plan 4.1].
 *
 * Each capability degrades on its own. The old flow demanded all three permissions and
 * parked forever on "En attente des permissions…" if any was denied — with no rationale, no
 * retry and no way to reach the system settings, so denying *location* (which a user who
 * only wants a spectrogram will do) bricked the app until reinstall.
 */
data class NvhPermissions(
    val microphone: Boolean,
    val preciseLocation: Boolean,
    val coarseLocation: Boolean,
) {
    /** Any location at all — enough to show a position, not enough to measure speed. */
    val anyLocation: Boolean get() = preciseLocation || coarseLocation

    /**
     * Only precise location may drive the metrological chain [GPS-12, GPS-3.2]: a coarse fix
     * carries no usable Doppler speed, so RPM/H1/order tracking must stay off.
     */
    val metrologicalLocation: Boolean get() = preciseLocation

    /** Live capture is possible; without it the app runs as a file analyzer. */
    val liveCapture: Boolean get() = microphone

    companion object {
        val REQUESTED =
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )

        fun read(context: Context): NvhPermissions =
            NvhPermissions(
                microphone = context.isGranted(Manifest.permission.RECORD_AUDIO),
                preciseLocation = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
                coarseLocation = context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
            )
    }
}

private fun Context.isGranted(permission: String): Boolean = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** LocalContext may be a wrapper; the rationale API only exists on the Activity itself. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Current grants, re-read every time the app comes back to the foreground.
 *
 * The resume refresh is what makes the "open the settings" path work: the user leaves for the
 * system settings screen, flips a permission, and comes back to an app that already knows.
 */
@Composable
fun rememberNvhPermissions(): NvhPermissions {
    val context = LocalContext.current
    var state by remember { mutableStateOf(NvhPermissions.read(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) state = NvhPermissions.read(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * Asks once, then hands [content] the resulting capability set — blocking only on the one
 * permission without which there is nothing to show at all.
 *
 * Microphone denied is NOT a dead end: the app still opens as a WAV/video analyzer, so a
 * recorded session can be re-analysed on a phone whose mic the user will not grant.
 */
@Composable
fun PermissionGate(
    onMicrophoneUnavailable: () -> Unit,
    content: @Composable (NvhPermissions) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permissions by remember { mutableStateOf(NvhPermissions.read(context)) }
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    var showAnalyzerOnly by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasAsked = true
            permissions = NvhPermissions.read(context)
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) permissions = NvhPermissions.read(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasAsked && !permissions.microphone) launcher.launch(NvhPermissions.REQUESTED)
    }

    // The system dialog can no longer appear: only the settings screen can grant now.
    val permanentlyDenied =
        hasAsked &&
            !permissions.microphone &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false

    LaunchedEffect(permissions.microphone, showAnalyzerOnly) {
        if (!permissions.microphone && showAnalyzerOnly) onMicrophoneUnavailable()
    }

    if (permissions.microphone || showAnalyzerOnly) {
        content(permissions)
    } else {
        MicrophoneRationaleScreen(
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(NvhPermissions.REQUESTED) },
            onOpenSettings = { context.openAppSettings() },
            onContinueWithoutMicrophone = { showAnalyzerOnly = true },
        )
    }
}

@Composable
private fun MicrophoneRationaleScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueWithoutMicrophone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = NvhSectionContainer),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎙️", fontSize = 40.sp)
                Text(
                    text = stringResource(R.string.perm_mic_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = NvhOnSurface,
                )
                Text(
                    text = stringResource(R.string.perm_mic_rationale),
                    fontSize = 13.sp,
                    color = NvhOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
                if (permanentlyDenied) {
                    Text(
                        text = stringResource(R.string.perm_mic_permanently_denied),
                        fontSize = 12.sp,
                        color = NvhStatusWarn,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(contentColor = NvhOnSurface),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (permanentlyDenied) R.string.perm_open_settings else R.string.perm_allow,
                                ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onContinueWithoutMicrophone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.perm_continue_without_mic))
                }
                Text(
                    text = stringResource(R.string.perm_analyzer_only_explanation),
                    fontSize = 11.sp,
                    color = NvhOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

/** Deep-link to this app's system settings page — the only route out of a permanent denial. */
fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
