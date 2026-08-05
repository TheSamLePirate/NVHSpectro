package com.example.nvhspectro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WavPlayerBar(
    fileName: String,
    currentPosMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStepSeconds: (Int) -> Unit
) {
    val currentSec = (currentPosMs / 1000L).coerceAtLeast(0)
    val totalSec = (totalDurationMs / 1000L).coerceAtLeast(1)

    val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
    val totalStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .border(1.dp, Color(0xFFD97706).copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
        color = Color(0xFF141A24),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Titre du fichier WAV chargé
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📂 LECTURE : $fileName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
                Text(
                    text = "$currentStr / $totalStr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Ligne de contrôle : [-10s] [▶/⏸] [+10s] + [Slider Scrubber]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Bouton -10s
                IconButton(
                    onClick = { onStepSeconds(-10) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("⏪", fontSize = 14.sp)
                }

                // Bouton Play / Pause
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 16.sp, color = Color.White)
                }

                // Bouton +10s
                IconButton(
                    onClick = { onStepSeconds(10) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("⏩", fontSize = 14.sp)
                }

                // Slider / Scrubber de position
                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF59E0B),
                        activeTrackColor = Color(0xFFD97706),
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}
