// Design-time previews [V14 UX-B3].
//
// The audit's Blocker 3: zero `@Preview` in the repository meant every visual change cost a
// full install-and-look cycle, and no component could be checked at font scale 1.3/2.0 or in
// landscape without a device. These previews close that loop for every parameter-pure
// component; screens that need a ViewModel are exercised on the emulator gate instead.
//
// The `@PreviewFontScale` multipreview on the bar and player renders each at the accessibility
// font scales — exactly where the previous 10 sp `softWrap = false` labels used to truncate.
package com.example.nvhspectro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import com.example.nvhspectro.AudioSourceMode
import com.example.nvhspectro.TelemetryData
import com.example.nvhspectro.data.EmergenceReportEntry
import com.example.nvhspectro.theme.NVHSpectroTheme
import com.example.nvhspectro.theme.NvhAccent
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhStatusBad
import com.example.nvhspectro.theme.NvhStatusGood
import com.example.nvhspectro.theme.NvhStatusWarn

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    NVHSpectroTheme {
        Surface { content() }
    }
}

@PreviewFontScale
@Composable
private fun MainBottomBarPreview() {
    PreviewSurface {
        MainBottomBar(
            isKinematicsEnabled = true,
            isReportModeActive = false,
            isFrozen = true,
            audioSourceMode = AudioSourceMode.WAV_ANALYZER,
            isVideoMode = false,
            isLiveCaptureAllowed = true,
            onOpenKinematics = {},
            onToggleReportMode = {},
            onToggleFreeze = {},
            onSelectAudioMode = {},
            onLiveDenied = {},
        )
    }
}

@PreviewFontScale
@Composable
private fun WavPlayerBarPreview() {
    PreviewSurface {
        WavPlayerBar(
            fileName = "Essai_Moteur_2026-08-28_Tres_Long_Nom.wav",
            currentPosMs = 83_000L,
            totalDurationMs = 254_000L,
            isPlaying = true,
            onPlayToggle = {},
            onSeekTo = {},
            onStepSeconds = {},
        )
    }
}

@Preview
@Composable
private fun StatusGlyphsPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(NvhSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            NvhStatusGlyph(NvhGlyphShape.DOT, NvhStatusGood)
            NvhStatusGlyph(NvhGlyphShape.TRIANGLE, NvhStatusWarn)
            NvhStatusGlyph(NvhGlyphShape.CROSS, NvhStatusBad)
            NvhStatusGlyph(NvhGlyphShape.BLOCKED, NvhStatusWarn)
        }
    }
}

@Preview
@Composable
private fun NvhSectionPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(NvhSpacing.lg)) {
            NvhSection(title = "SECTION D'EXEMPLE", accent = NvhAccent) {
                EmergenceReportRow(
                    EmergenceReportEntry(
                        orderName = "H18",
                        orderValue = 18.0,
                        minSpeedKmh = 32f,
                        maxSpeedKmh = 74f,
                        minRpm = 3200,
                        maxRpm = 7400,
                        minFreqHz = 960,
                        maxFreqHz = 2220,
                        maxEmergenceDb = 8.4,
                    ),
                )
            }
        }
    }
}

@PreviewFontScale
@Composable
private fun OrderSelectionDialogPreview() {
    PreviewSurface {
        OrderSelectionDialog(
            currentOrder = 18.0,
            onOrderSelected = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun SaveRecordingDialogPreview() {
    PreviewSurface {
        SaveRecordingDialog(
            durationSec = 42,
            onSave = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ExportDialogPreview() {
    PreviewSurface {
        ExportDialog(
            onDismiss = {},
            telemetry = TelemetryData(speedKmh = 63.4f),
            onExport = { _, _ -> },
        )
    }
}
