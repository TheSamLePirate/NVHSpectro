package com.example.nvhspectro

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [L1, L2, L4, L6 — plan 2.3] The one owner of the playback MediaPlayer.
 *
 * - prepareAsync via a suspending API — no more synchronous prepare() on the
 *   main thread [L4].
 * - Original vs filtered sources are explicit (replaces the fragile
 *   "filtered_playback.wav" filename check) [L2].
 * - Every acquisition path releases the previous player; release() is
 *   idempotent and called from onCleared [L1].
 */
class PlaybackController {

    private var mediaPlayer: MediaPlayer? = null
    private var originalFile: File? = null
    private var originalUri: Uri? = null
    private var appContext: Context? = null

    /** Invoked on the player thread when playback reaches the end of the source. */
    var onCompletion: (() -> Unit)? = null

    val isPlaying: Boolean
        get() = safeGet { mediaPlayer?.isPlaying == true } ?: false

    val currentPositionMs: Int
        get() = safeGet { mediaPlayer?.currentPosition } ?: 0

    /**
     * Prepare a NEW original source (file or content uri). Suspends until the
     * player is prepared; returns the media duration in ms, or null on failure.
     */
    suspend fun setOriginalSource(context: Context?, file: File?, uri: Uri?): Long? {
        originalFile = file
        originalUri = uri
        appContext = context?.applicationContext ?: appContext
        return prepare(file, uri)
    }

    /** Swap in a filtered rendering of the same source; original refs are kept. */
    suspend fun setFilteredSource(file: File): Long? = prepare(file, null)

    /** Back to the unfiltered original (when the filter chain empties). */
    suspend fun restoreOriginalSource(): Long? = prepare(originalFile, originalUri)

    fun play() = safe { mediaPlayer?.start() }

    fun pause() = safe {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    fun seekTo(positionMs: Int) = safe { mediaPlayer?.seekTo(positionMs) }

    fun release() {
        safe { mediaPlayer?.stop() }
        safe { mediaPlayer?.release() }
        mediaPlayer = null
    }

    private suspend fun prepare(file: File?, uri: Uri?): Long? {
        release()
        if (file == null && uri == null) return null
        return try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            if (file != null) {
                mp.setDataSource(file.absolutePath)
            } else {
                val ctx = appContext ?: return null
                mp.setDataSource(ctx, uri!!)
            }
            mp.setOnCompletionListener { onCompletion?.invoke() }
            suspendCancellableCoroutine { cont ->
                mp.setOnPreparedListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, what, extra ->
                    if (cont.isActive) cont.resumeWithException(IOException("MediaPlayer error $what/$extra"))
                    true
                }
                cont.invokeOnCancellation { release() }
                mp.prepareAsync() // [L4] never a blocking prepare on the main thread
            }
            mp.duration.toLong().takeIf { it > 0 }
        } catch (e: Exception) {
            release()
            null
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // IllegalStateException on a released player is expected during teardown.
        }
    }

    private inline fun <T> safeGet(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        null
    }
}
