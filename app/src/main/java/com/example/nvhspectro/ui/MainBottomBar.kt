// The app's primary mode bar [V14 UX-M5, UX-M6, UX-B1].
//
// Previously five filled `Button`s sharing `weight(1f)` with 2 dp content padding and
// 10–11 sp `softWrap = false` labels — the least legible surface in the app — plus a `Popup`
// whose offset was given in PIXELS, so the audio-source menu landed in a different place on
// every screen density. A `NavigationBar` gives the four persistent modes the pill-indicator
// selection Android users already read as "current mode", and `DropdownMenu` anchors itself
// to its item, animates, and dismisses on outside touch — the density bug cannot exist.
package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.nvhspectro.AudioSourceMode
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhActiveContainer
import com.example.nvhspectro.theme.NvhModeLive
import com.example.nvhspectro.theme.NvhModeVideo
import com.example.nvhspectro.theme.NvhModeWav
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhRecording
import com.example.nvhspectro.theme.NvhReportMode

@Composable
fun MainBottomBar(
    isKinematicsEnabled: Boolean,
    isReportModeActive: Boolean,
    isFrozen: Boolean,
    audioSourceMode: AudioSourceMode,
    isVideoMode: Boolean,
    isLiveCaptureAllowed: Boolean,
    onOpenKinematics: () -> Unit,
    onToggleReportMode: () -> Unit,
    onToggleFreeze: () -> Unit,
    onSelectAudioMode: (AudioSourceMode) -> Unit,
    onLiveDenied: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var showSourceMenu by remember { mutableStateOf(false) }

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        // 1. GMPe kinematics — selected while the kinematic chain is engaged.
        val gmpeActiveLabel = stringResource(R.string.cd_gmpe_active)
        NavigationBarItem(
            selected = isKinematicsEnabled,
            onClick = onOpenKinematics,
            enabled = !isVideoMode,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = if (isKinematicsEnabled) gmpeActiveLabel else null,
                )
            },
            label = { BarLabel(stringResource(R.string.bar_gmpe)) },
            colors = nvhBarItemColors(indicator = NvhActiveContainer),
        )

        // 2. Manual report mode.
        NavigationBarItem(
            selected = isReportModeActive,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleReportMode()
            },
            enabled = !isVideoMode,
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            label = { BarLabel(stringResource(R.string.bar_report)) },
            colors = nvhBarItemColors(indicator = NvhReportMode),
        )

        // 3. Freeze — an attention state, so its pill is the recording red.
        NavigationBarItem(
            selected = isFrozen,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleFreeze()
            },
            icon = {
                Icon(
                    imageVector = if (isFrozen) Icons.Filled.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = null,
                )
            },
            label = {
                BarLabel(stringResource(if (isFrozen) R.string.bar_unfreeze else R.string.bar_freeze))
            },
            colors = nvhBarItemColors(indicator = NvhRecording),
        )

        // 4. Audio source — always "selected" in its mode colour; tapping opens the menu,
        // which anchors to this item (no hand-tuned offsets, no density dependence).
        val (sourceIcon, sourceLabel, sourceColor) =
            when (audioSourceMode) {
                AudioSourceMode.WAV_ANALYZER ->
                    Triple(Icons.Outlined.GraphicEq, stringResource(R.string.bar_source_wav), NvhModeWav)
                AudioSourceMode.VIDEO ->
                    Triple(Icons.Outlined.Videocam, stringResource(R.string.bar_source_video), NvhModeVideo)
                else -> Triple(Icons.Outlined.Mic, stringResource(R.string.bar_source_live), NvhModeLive)
            }
        val sourceMenuLabel = stringResource(R.string.cd_source_menu)
        NavigationBarItem(
            selected = true,
            onClick = { showSourceMenu = true },
            icon = {
                Box {
                    Icon(imageVector = sourceIcon, contentDescription = sourceMenuLabel)
                    SourceMenu(
                        expanded = showSourceMenu,
                        audioSourceMode = audioSourceMode,
                        isLiveCaptureAllowed = isLiveCaptureAllowed,
                        onDismiss = { showSourceMenu = false },
                        onSelect = { mode ->
                            showSourceMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectAudioMode(mode)
                        },
                        onLiveDenied = {
                            showSourceMenu = false
                            onLiveDenied()
                        },
                    )
                }
            },
            label = { BarLabel(sourceLabel) },
            colors = nvhBarItemColors(indicator = sourceColor),
        )
    }
}

/** One label construction for the whole bar: ellipsis, never a hard truncation. */
@Composable
private fun BarLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun nvhBarItemColors(indicator: Color) =
    NavigationBarItemDefaults.colors(
        selectedIconColor = NvhOnSurface,
        selectedTextColor = NvhOnSurface,
        indicatorColor = indicator,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
private fun SourceMenu(
    expanded: Boolean,
    audioSourceMode: AudioSourceMode,
    isLiveCaptureAllowed: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AudioSourceMode) -> Unit,
    onLiveDenied: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        SourceMenuItem(
            label =
                stringResource(
                    if (isLiveCaptureAllowed) R.string.source_live else R.string.source_live_denied,
                ),
            icon = if (isLiveCaptureAllowed) Icons.Outlined.Mic else Icons.Outlined.MicOff,
            selected = audioSourceMode == AudioSourceMode.LIVE,
            // [U1] Never a silently dead control: the denied entry stays tappable and
            // explains itself through a notice.
            onClick = { if (isLiveCaptureAllowed) onSelect(AudioSourceMode.LIVE) else onLiveDenied() },
        )
        SourceMenuItem(
            label = stringResource(R.string.source_wav),
            icon = Icons.Outlined.GraphicEq,
            selected = audioSourceMode == AudioSourceMode.WAV_ANALYZER,
            onClick = { onSelect(AudioSourceMode.WAV_ANALYZER) },
        )
        SourceMenuItem(
            label = stringResource(R.string.source_video),
            icon = Icons.Outlined.Videocam,
            selected = audioSourceMode == AudioSourceMode.VIDEO,
            onClick = { onSelect(AudioSourceMode.VIDEO) },
        )
    }
}

@Composable
private fun SourceMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(),
    )
}
