package com.example.nvhspectro.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhCanvas
import com.example.nvhspectro.theme.NvhMinTouchTarget
import com.example.nvhspectro.theme.NvhModeVideo
import com.example.nvhspectro.theme.NvhModeVideoAccent
import com.example.nvhspectro.theme.NvhOnSurface
import com.example.nvhspectro.theme.NvhOnSurfaceVariant
import com.example.nvhspectro.theme.NvhOutline
import com.example.nvhspectro.theme.NvhReadoutSmall
import com.example.nvhspectro.theme.NvhSectionContainer
import com.example.nvhspectro.theme.NvhSpacing
import java.util.Locale
import kotlin.math.abs

/**
 * Beyond this, the (muted) picture is re-seeked to the audio clock [U6, plan 4.8].
 * One analysis frame at 43 fps is ~23 ms; a quarter second is imperceptible drift but well
 * above the jitter of `VideoView.currentPosition`, so it does not thrash the decoder.
 */
private const val MAX_VIDEO_DRIFT_MS = 250L

private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000

/**
 * Video mode's picture and transport [U6, plan 4.8].
 *
 * The picture is always muted: the audio the analyst hears is the *analysed* PCM, played by
 * `PlaybackController`, which is the single owner of the analysis clock. The video only ever
 * follows it. The YouTube source was deleted with decision D7 — it loaded user-supplied URLs
 * into a JavaScript-enabled WebView and analysed nothing at all [V2].
 */
@Composable
fun VideoPlayerView(
    videoUri: Uri?,
    videoTitle: String,
    state: VideoPlaybackState,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenVideoSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(NvhSpacing.sm),
        colors = CardDefaults.cardColors(containerColor = NvhSectionContainer),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (videoUri == null) {
            NoVideoLoaded(onOpenVideoSelection)
        } else {
            LoadedVideo(
                videoUri = videoUri,
                videoTitle = videoTitle,
                state = state,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause,
            )
        }
    }
}

@Composable
private fun NoVideoLoaded(onOpenVideoSelection: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NvhSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = NvhOnSurfaceVariant,
                modifier = Modifier.padding(bottom = NvhSpacing.xs),
            )
            Text(
                text = stringResource(R.string.video_mode_title),
                style = MaterialTheme.typography.titleMedium,
                color = NvhOnSurface,
            )
            Text(
                text = stringResource(R.string.video_none_loaded),
                style = MaterialTheme.typography.bodyMedium,
                color = NvhOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = NvhSpacing.lg),
            )
            Button(
                onClick = onOpenVideoSelection,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = NvhModeVideo,
                        contentColor = NvhOnSurface,
                    ),
            ) {
                Text(text = stringResource(R.string.video_load))
            }
        }
    }
}

/** Everything the transport row and the picture need, in one value. */
data class VideoPlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

@Composable
private fun LoadedVideo(
    videoUri: Uri,
    videoTitle: String,
    state: VideoPlaybackState,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(NvhSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = videoTitle,
                style = MaterialTheme.typography.titleSmall,
                color = NvhOnSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = NvhSpacing.sm),
            )
            // [V14 UX-M1] Tabular figures — a running timecode must not jitter.
            Text(
                text = formatTime(state.positionMs) + " / " + formatTime(state.durationMs),
                style = NvhReadoutSmall,
                color = NvhModeVideoAccent,
            )
        }

        Spacer(modifier = Modifier.height(NvhSpacing.xs))

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(NvhCanvas),
            contentAlignment = Alignment.Center,
        ) {
            MutedVideoSurface(videoUri = videoUri, isPlaying = state.isPlaying, positionMs = state.positionMs)
        }

        Spacer(modifier = Modifier.height(NvhSpacing.sm))

        VideoTransportRow(state = state, onSeekTo = onSeekTo, onTogglePlayPause = onTogglePlayPause)
    }
}

@Composable
private fun VideoTransportRow(
    state: VideoPlaybackState,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NvhSpacing.sm),
    ) {
        val playPauseLabel = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play)
        val positionLabel = stringResource(R.string.cd_playback_position)
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(NvhMinTouchTarget).semantics { contentDescription = playPauseLabel },
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = NvhOnSurface,
            )
        }
        Slider(
            value = state.positionMs.toFloat(),
            onValueChange = { onSeekTo(it.toLong()) },
            valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.weight(1f).semantics { contentDescription = positionLabel },
            colors =
                SliderDefaults.colors(
                    thumbColor = NvhModeVideoAccent,
                    activeTrackColor = NvhModeVideoAccent,
                    inactiveTrackColor = NvhOutline,
                ),
        )
    }
}

@Composable
private fun MutedVideoSurface(
    videoUri: Uri,
    isPlaying: Boolean,
    positionMs: Long,
) {
    key(videoUri) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        // Muted: the audio the analyst hears is the analysed PCM, so picture
                        // and spectrum can never disagree about what is being measured.
                        mp.setVolume(0f, 0f)
                    }
                }
            },
            update = { videoView ->
                // [U6, plan 4.8] Re-seek whenever the picture drifts from the audio clock,
                // not only on play/pause transitions: the old code seeked ONLY when starting
                // playback, so scrubbing while playing left the image permanently out of sync
                // with the spectrum being analysed.
                if (abs(videoView.currentPosition - positionMs) > MAX_VIDEO_DRIFT_MS) {
                    videoView.seekTo(positionMs.toInt())
                }
                if (isPlaying && !videoView.isPlaying) {
                    videoView.start()
                } else if (!isPlaying && videoView.isPlaying) {
                    videoView.pause()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / MILLIS_PER_SECOND).coerceAtLeast(0)
    // Locale.ROOT: a technical timecode must read the same on every device [C11 class].
    return String.format(Locale.ROOT, "%02d:%02d", totalSec / SECONDS_PER_MINUTE, totalSec % SECONDS_PER_MINUTE)
}

