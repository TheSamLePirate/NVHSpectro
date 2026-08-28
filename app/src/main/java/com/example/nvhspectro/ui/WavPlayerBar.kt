package com.example.nvhspectro.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhAlpha
import com.example.nvhspectro.theme.NvhElevation
import com.example.nvhspectro.theme.NvhMinTouchTarget
import com.example.nvhspectro.theme.NvhModeWav
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSpacing
import com.example.nvhspectro.theme.NvhSurfaceVariant

@Composable
fun WavPlayerBar(
    fileName: String,
    currentPosMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStepSeconds: (Int) -> Unit,
) {
    val currentSec = (currentPosMs / 1000L).coerceAtLeast(0)
    val totalSec = (totalDurationMs / 1000L).coerceAtLeast(1)

    val currentStr = formatMinSec(currentSec)
    val totalStr = formatMinSec(totalSec)

    val backLabel = stringResource(R.string.cd_step_back, STEP_SECONDS)
    val forwardLabel = stringResource(R.string.cd_step_forward, STEP_SECONDS)
    val playLabel = stringResource(R.string.cd_play)
    val pauseLabel = stringResource(R.string.cd_pause)
    val positionLabel = stringResource(R.string.cd_playback_position)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs)
                .border(1.dp, NvhModeWavAccent.copy(alpha = NvhAlpha.STRONG), MaterialTheme.shapes.medium),
        color = NvhSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = NvhElevation.raised,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NvhSpacing.sm, vertical = NvhSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.xxs),
        ) {
            // Titre du fichier WAV chargé + timecode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_reading, fileName),
                    style = MaterialTheme.typography.labelMedium,
                    color = NvhModeWavAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = NvhSpacing.sm),
                )
                // [V14 UX-M1] Tabular figures — a running timecode must not jitter.
                Text(
                    text = stringResource(R.string.player_position, currentStr, totalStr),
                    style = NvhReadoutSmall,
                    color = NvhOnSurface,
                )
            }

            // Ligne de contrôle : [-10s] [lecture/pause] [+10s] + [Slider Scrubber]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NvhSpacing.xs),
            ) {
                // [§12, plan 4.4] 48 dp targets; the icons are themable vectors with real
                // metrics [V14 UX-M2] and each control keeps its spoken label — through
                // TalkBack a glyph is not a word.
                IconButton(
                    onClick = { onStepSeconds(-STEP_SECONDS) },
                    modifier = Modifier.size(NvhMinTouchTarget).semantics { contentDescription = backLabel },
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = null)
                }

                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier =
                        Modifier.size(NvhMinTouchTarget).semantics {
                            contentDescription = if (isPlaying) pauseLabel else playLabel
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NvhModeWav),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = NvhOnSurface,
                    )
                }

                IconButton(
                    onClick = { onStepSeconds(STEP_SECONDS) },
                    modifier = Modifier.size(NvhMinTouchTarget).semantics { contentDescription = forwardLabel },
                ) {
                    Icon(Icons.Filled.Forward10, contentDescription = null)
                }

                // Slider / Scrubber de position
                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f).semantics { contentDescription = positionLabel },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = NvhModeWavAccent,
                            activeTrackColor = NvhModeWav,
                            inactiveTrackColor = NvhOutline,
                        ),
                )
            }
        }
    }
}

private const val STEP_SECONDS = 10

/** mm:ss, locale-independent [C11 class]. */
private fun formatMinSec(totalSeconds: Long): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
