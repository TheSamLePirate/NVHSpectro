package com.example.nvhspectro.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/** One saved measurement: WAV plus optional telemetry JSON sidecar. */
data class RecordingEntry(
    val displayName: String,
    val wavUri: Uri,
    val jsonUri: Uri?,
    val addedAtMs: Long
)

/**
 * Recording persistence [audit C4/S3, plan 1.7].
 *
 * API 29+: MediaStore.Downloads under Download/NVH_Spectro_Exports/<name>/ —
 * the supported path on scoped storage (the old direct-File writes failed
 * silently on Android 10). Below API 29 the legacy public-directory path is
 * used; failures there surface to the caller instead of losing data silently.
 * All methods are blocking — call from Dispatchers.IO.
 */
object RecordingStore {

    const val COLLECTION_DIR = "NVH_Spectro_Exports"

    /** Writes WAV + JSON. Throws IOException with a readable message on failure. */
    @Throws(IOException::class)
    fun saveRecording(
        context: Context,
        baseName: String,
        pcm: ShortArray,
        sampleRate: Int,
        telemetryJson: String
    ) {
        if (Build.VERSION.SDK_INT >= 29) {
            saveViaMediaStore(context, baseName, pcm, sampleRate, telemetryJson)
        } else {
            saveViaLegacyFiles(baseName, pcm, sampleRate, telemetryJson)
        }
    }

    /** Newest-first list of saved recordings. Best-effort: returns what it can, never throws. */
    fun listRecordings(context: Context): List<RecordingEntry> {
        val byName = LinkedHashMap<String, RecordingEntry>()
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                listViaMediaStore(context).forEach { byName[it.displayName] = it }
            } catch (e: Exception) {
                // fall through to legacy listing
            }
        }
        try {
            listViaLegacyFiles().forEach { byName.putIfAbsent(it.displayName, it) }
        } catch (e: Exception) {
            // best effort
        }
        return byName.values.sortedByDescending { it.addedAtMs }
    }

    /** Reads a small text sidecar (telemetry JSON). Null on any failure. */
    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------

    @RequiresApi(29)
    private fun saveViaMediaStore(
        context: Context,
        baseName: String,
        pcm: ShortArray,
        sampleRate: Int,
        telemetryJson: String
    ) {
        val resolver = context.contentResolver
        val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + COLLECTION_DIR + "/" + baseName

        fun insertAndWrite(displayName: String, mime: String, write: (java.io.OutputStream) -> Unit): Uri {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore a refusé la création de $displayName")
            try {
                resolver.openOutputStream(uri)?.use(write)
                    ?: throw IOException("Flux d'écriture indisponible pour $displayName")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw if (e is IOException) e else IOException("Écriture de $displayName échouée : ${e.message}")
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        insertAndWrite("$baseName.wav", "audio/wav") { out ->
            WavAudioWriter.writePcmToStream(pcm, out, sampleRate)
        }
        insertAndWrite("${baseName}_telemetrie.json", "application/json") { out ->
            out.write(telemetryJson.toByteArray(Charsets.UTF_8))
        }
    }

    private fun saveViaLegacyFiles(baseName: String, pcm: ShortArray, sampleRate: Int, telemetryJson: String) {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloadsDir, "$COLLECTION_DIR/$baseName")
        if (!folder.mkdirs() && !folder.isDirectory) {
            throw IOException("Dossier inaccessible : ${folder.absolutePath} (permission stockage requise sur cet Android)")
        }
        WavAudioWriter.writePcmToWav(pcm, File(folder, "$baseName.wav"), sampleRate)
        File(folder, "${baseName}_telemetrie.json").writeText(telemetryJson)
    }

    @RequiresApi(29)
    private fun listViaMediaStore(context: Context): List<RecordingEntry> {
        val resolver = context.contentResolver
        val wavs = mutableMapOf<String, Pair<Uri, Long>>() // base name -> (uri, added)
        val jsons = mutableMapOf<String, Uri>()

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED
            ),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("%$COLLECTION_DIR%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                when {
                    name.endsWith(".wav", ignoreCase = true) ->
                        wavs[name.removeSuffix(".wav")] = uri to cursor.getLong(addedCol) * 1000L
                    name.endsWith("_telemetrie.json", ignoreCase = true) ->
                        jsons[name.removeSuffix("_telemetrie.json")] = uri
                }
            }
        }
        return wavs.map { (base, wav) ->
            RecordingEntry(base, wav.first, jsons[base], wav.second)
        }
    }

    private fun listViaLegacyFiles(): List<RecordingEntry> {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val parent = File(downloadsDir, COLLECTION_DIR)
        if (!parent.isDirectory) return emptyList()
        return parent.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val wav = dir.listFiles()?.firstOrNull { it.name.endsWith(".wav", ignoreCase = true) }
                ?: return@mapNotNull null
            val json = dir.listFiles()?.firstOrNull { it.name.endsWith(".json", ignoreCase = true) }
            RecordingEntry(
                displayName = dir.name,
                wavUri = Uri.fromFile(wav),
                jsonUri = json?.let(Uri::fromFile),
                addedAtMs = wav.lastModified()
            )
        } ?: emptyList()
    }
}
