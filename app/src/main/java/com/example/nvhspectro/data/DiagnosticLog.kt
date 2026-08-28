package com.example.nvhspectro.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A local, rotating diagnostic log [V3, plan 4.7].
 *
 * The audit's error-handling census: 17 `catch` blocks, 10 ending in `printStackTrace()` and
 * the rest silent; no logging framework, no crash reporting, and — for a field tool — no way
 * for an operator to tell anyone *what* went wrong. A failed save, an unreadable WAV or a mic
 * that would not open meant a lost test session with no explanation and nothing to send.
 *
 * Deliberate properties:
 *  - **Local only.** The app has no INTERNET permission and this must not create a reason to
 *    add one. Nothing is uploaded; the file leaves the device only through an explicit,
 *    user-initiated share.
 *  - **Bounded.** Two files of [MAX_BYTES]; the current one rotates onto the previous, which
 *    is discarded. A measurement instrument must not fill a phone with logs.
 *  - **Off the caller's thread.** A single writer thread, so logging from the DSP or capture
 *    path never blocks it [C6].
 *  - **Never fatal.** Logging failures are swallowed after one Logcat warning: an error while
 *    reporting an error must not become the error.
 */
object DiagnosticLog {
    private const val TAG = "NvhDiag"
    private const val DIR = "diagnostics"
    private const val CURRENT = "nvh-diagnostic.log"
    private const val PREVIOUS = "nvh-diagnostic.1.log"
    const val MAX_BYTES = 256L * 1024L

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "nvh-diag").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    @Volatile private var dir: File? = null

    @Volatile private var failed = false

    /** Call once at process start; safe to repeat. */
    fun init(context: Context) {
        if (dir != null) return
        runCatching { File(context.filesDir, DIR).apply { mkdirs() } }
            .onSuccess { dir = it }
            .onFailure { Log.w(TAG, "diagnostic log unavailable", it) }
    }

    fun i(
        tag: String,
        message: String,
    ) = write("INFO ", tag, message, null)

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = write("WARN ", tag, message, error)

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = write("ERROR", tag, message, error)

    /** The user-facing notices (banner messages) are logged verbatim, so a report matches. */
    fun notice(message: String) = write("NOTE ", "Notice", message, null)

    private fun write(
        level: String,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        when (level) {
            "ERROR" -> Log.e(tag, message, error)
            "WARN " -> Log.w(tag, message, error)
            else -> Log.i(tag, message)
        }
        val target = dir ?: return
        if (failed) return
        val time = synchronized(stamp) { stamp.format(Date()) }
        writer.execute {
            runCatching {
                val current = File(target, CURRENT)
                if (current.length() > MAX_BYTES) {
                    val previous = File(target, PREVIOUS)
                    previous.delete()
                    current.renameTo(previous)
                }
                val trace = error?.let { "\n    ${it.javaClass.name}: ${it.message}" } ?: ""
                File(target, CURRENT).appendText("$time $level [$tag] $message$trace\n")
            }.onFailure {
                // Fail once, loudly, then stay quiet: a broken log must not spam Logcat.
                failed = true
                Log.w(TAG, "diagnostic log write failed; disabling", it)
            }
        }
    }

    /** The file to share, or null when nothing has been logged yet. */
    fun currentFile(): File? = dir?.let { File(it, CURRENT) }?.takeIf { it.exists() && it.length() > 0 }

    /** Bytes currently held by both log files — shown next to the share action. */
    fun sizeBytes(): Long = dir?.let { File(it, CURRENT).length() + File(it, PREVIOUS).length() } ?: 0L

    /** Both files, oldest first, for a share that should carry the full window. */
    fun clear() {
        val target = dir ?: return
        writer.execute {
            runCatching {
                File(target, CURRENT).delete()
                File(target, PREVIOUS).delete()
            }
        }
    }
}
