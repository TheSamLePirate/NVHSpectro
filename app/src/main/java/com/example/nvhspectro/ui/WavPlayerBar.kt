package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhModeWav
import com.example.nvhspectro.theme.NvhModeWavAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOutline
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
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .border(1.dp, NvhModeWavAccent.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
        color = NvhSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Titre du fichier WAV chargé
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_reading, fileName),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NvhModeWavAccent,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.player_position, currentStr, totalStr),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NvhOnSurface,
                )
            }

            // Ligne de contrôle : [-10s] [▶/⏸] [+10s] + [Slider Scrubber]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // [§12, plan 4.4] 48 dp targets, and every emoji-as-icon control carries a
                // spoken label — through TalkBack "⏪" is not a word.
                IconButton(
                    onClick = { onStepSeconds(-STEP_SECONDS) },
                    modifier = Modifier.size(PLAYER_TOUCH_TARGET).semantics { contentDescription = backLabel },
                ) {
                    Text("⏪", fontSize = 14.sp)
                }

                // Bouton Play / Pause
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier =
                        Modifier.size(PLAYER_TOUCH_TARGET).semantics {
                            contentDescription = if (isPlaying) pauseLabel else playLabel
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NvhModeWav),
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 16.sp, color = NvhOnSurface)
                }

                // Bouton +10s
                IconButton(
                    onClick = { onStepSeconds(STEP_SECONDS) },
                    modifier = Modifier.size(PLAYER_TOUCH_TARGET).semantics { contentDescription = forwardLabel },
                ) {
                    Text("⏩", fontSize = 14.sp)
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

/** 48 dp minimum interactive size [§12, plan 4.4]. */
private val PLAYER_TOUCH_TARGET = 48.dp

private const val STEP_SECONDS = 10

/** mm:ss, locale-independent [C11 class]. */
private fun formatMinSec(totalSeconds: Long): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
