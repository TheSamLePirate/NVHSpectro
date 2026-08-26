package com.example.nvhspectro.data

import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Debug-build drive logger — AAA plan step 0.8.
 *
 * Appends every raw GNSS/fused fix to a CSV under the app's external files dir
 * (no storage permission needed; pull with
 * `adb pull /sdcard/Android/data/<pkg>/files/field_logs`). The recorded
 * `elapsedRealtimeNanos` + `speedAccuracy` columns are exactly the data the
 * Phase 2 speed estimator (plan 2.4, audit G1–G4) will be tuned against.
 *
 * Must never affect the app: all I/O on its own single thread, all failures
 * swallowed after one log line. Callers gate on BuildConfig.DEBUG.
 */
class FieldLocationLogger(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "field-location-logger").apply { isDaemon = true }
        }
    private var writer: BufferedWriter? = null
    private var failed = false
    private var linesSinceFlush = 0

    fun log(location: Location) {
        // Capture values on the caller thread; Location objects are recycled.
        val elapsedNs = location.elapsedRealtimeNanos
        val utcMs = location.time
        val provider = location.provider ?: "?"
        val lat = location.latitude
        val lon = location.longitude
        val alt = if (location.hasAltitude()) location.altitude else Double.NaN
        val speed = if (location.hasSpeed()) location.speed else Float.NaN
        val speedAcc =
            if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) {
                location.speedAccuracyMetersPerSecond
            } else {
                Float.NaN
            }
        val horizAcc = if (location.hasAccuracy()) location.accuracy else Float.NaN
        val bearing = if (location.hasBearing()) location.bearing else Float.NaN

        executor.execute {
            if (failed) return@execute
            try {
                val w = writer ?: openWriter().also { writer = it }
                w.write(
                    String.format(
                        Locale.US,
                        "%d,%d,%s,%.7f,%.7f,%.1f,%.3f,%.3f,%.1f,%.1f%n",
                        elapsedNs,
                        utcMs,
                        provider,
                        lat,
                        lon,
                        alt,
                        speed,
                        speedAcc,
                        horizAcc,
                        bearing,
                    ),
                )
                if (++linesSinceFlush >= FLUSH_EVERY) {
                    w.flush()
                    linesSinceFlush = 0
                }
            } catch (e: Exception) {
                failed = true
                Log.w(TAG, "field logging disabled after error", e)
            }
        }
    }

    private fun openWriter(): BufferedWriter {
        val dir = File(appContext.getExternalFilesDir(null), "field_logs")
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "drive_$stamp.csv")
        val w = BufferedWriter(FileWriter(file, true))
        w.write("elapsedRealtimeNanos,utcTimeMs,provider,lat,lon,altM,speedMs,speedAccMs,horizAccM,bearingDeg")
        w.newLine()
        Log.i(TAG, "field log: ${file.absolutePath}")
        return w
    }

    private companion object {
        const val TAG = "FieldLocationLogger"
        const val FLUSH_EVERY = 10
    }
}
