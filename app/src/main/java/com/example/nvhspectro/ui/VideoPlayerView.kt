package com.example.nvhspectro.ui

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPlayerView(
    videoUri: Uri?,
    youtubeUrl: String?,
    videoTitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenVideoSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVideo = (videoUri != null || !youtubeUrl.isNullOrBlank())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101827)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (!hasVideo) {
            // Écran initial : Aucune donnée vidéo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🎬 Mode Analyse Vidéo Synchronisé",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Pas de données vidéo. Cliquez sur [Charger Vidéo] à côté de TTNR pour ouvrir un fichier local ou un lien YouTube (limité à 5 min max).",
                        fontSize = 13.sp,
                        color = Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Button(
                        onClick = onOpenVideoSelection,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "📂 Charger Vidéo (Local / YouTube)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Vidéo chargée : affichage de la vidéo + contrôleur
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Titre de la vidéo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📹 $videoTitle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1
                    )

                    Text(
                        text = formatTime(positionMs) + " / " + formatTime(durationMs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Zone Vidéo Android (VideoView pour fichier local / WebView pour YouTube)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUri != null) {
                        key(videoUri) {
                            AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(videoUri)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        mp.setVolume(0f, 0f) // Mute the video to let MediaPlayer handle audio in sync with FFT
                                    }
                                }
                            },
                            update = { videoView ->
                                if (isPlaying && !videoView.isPlaying) {
                                    videoView.seekTo(positionMs.toInt())
                                    videoView.start()
                                } else if (!isPlaying && videoView.isPlaying) {
                                    videoView.pause()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    } else if (!youtubeUrl.isNullOrBlank()) {
                        key(youtubeUrl) {
                            AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    webViewClient = WebViewClient()
                                    val embedUrl = parseYouTubeEmbedUrl(youtubeUrl ?: "")
                                    loadUrl(embedUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Barre de lecture & Slider de positionnement
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E676),
                            activeTrackColor = Color(0xFF00E676),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun parseYouTubeEmbedUrl(url: String): String {
    val videoId = when {
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
        else -> url
    }
    return "https://www.youtube.com/embed/$videoId?autoplay=1"
}
