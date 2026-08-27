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
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Debug-build drive logger — AAA plan step 0.8, schema v2 per plan-gps GPS-0.4.
 *
 * Appends every raw GNSS/fused fix PLUS the estimator's outcome for it to a
 * CSV under the app's external files dir (no storage permission needed; pull
 * with `adb pull /sdcard/Android/data/<pkg>/files/field_logs`). The format is
 * [FieldTraceV2] — pure, round-trip-tested — and is the dataset the GPS-2
 * Kalman is tuned against [GPS-13]. The header carries an anonymized identity:
 * a random per-install UUID plus the device model (needed for the GPS-5
 * device matrix; no serial, no account data).
 *
 * Time bases [GPS-0.5]: nanos columns are BOOTTIME; utcTimeMs is Location.time
 * for human labeling only [G1]. Absent values are empty fields, never NaN.
 *
 * Must never affect the app: all I/O on its own single thread, all failures
 * swallowed after one log line. Callers gate on BuildConfig.DEBUG.
 */
class FieldLocationLogger(
    context: Context,
    /** [GPS-3.5] Free-form `key=value` capability matrix for the trace header. */
    private val capabilities: String? = null,
) {
    private val appContext = context.applicationContext
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "field-location-logger").apply { isDaemon = true }
        }
    private var writer: BufferedWriter? = null
    private var failed = false
    private var linesSinceFlush = 0

    /**
     * [GPS-0.4] One row per fix: raw Location fields + estimator state,
     * validity, rejection reason and NIS at delivery time.
     */
    fun log(
        location: Location,
        callbackTimeNanos: Long,
        isMock: Boolean,
        outcome: EstimatorOutcome,
        gnss: GnssDiagnostics?,
    ) {
        // Capture values on the caller thread; Location objects are recycled.
        val estimate = outcome.estimate
        val record =
            FieldTraceV2.Record(
                fixTimeNanos = location.elapsedRealtimeNanos,
                callbackTimeNanos = callbackTimeNanos,
                utcTimeMs = location.time,
                provider = location.provider ?: "?",
                isMock = isMock,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeM = if (location.hasAltitude()) location.altitude else null,
                rawSpeedMps = if (location.hasSpeed()) location.speed else null,
                speedSigmaMps =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                        location.speedAccuracyMetersPerSecond
                    } else {
                        null
                    },
                horizontalAccuracyM = if (location.hasAccuracy()) location.accuracy else null,
                bearingDeg = if (location.hasBearing()) location.bearing else null,
                estimatedSpeedMps = estimate.speedMps,
                estimatedAccelMps2 = estimate.accelerationMps2,
                estimatedSpeedSigmaMps = estimate.speedSigmaMps,
                validity = estimate.validity,
                ageSinceFixNanos = estimate.ageSinceFixNanos,
                rejection = outcome.rejection,
                nis = outcome.nis,
                gnss = gnss,
            )

        executor.execute {
            if (failed) return@execute
            try {
                val w = writer ?: openWriter().also { writer = it }
                w.write(FieldTraceV2.encodeRow(record))
                w.newLine()
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
        w.write(
            FieldTraceV2.encodeHeader(
                FieldTraceV2.Metadata(
                    schemaVersion = FieldTraceV2.SCHEMA_VERSION,
                    installId = installId(dir),
                    deviceModel = Build.MODEL ?: "?",
                    capabilities = capabilities,
                ),
            ),
        )
        w.newLine()
        Log.i(TAG, "field log: ${file.absolutePath}")
        return w
    }

    /** Random UUID minted once per install — the anonymized device id [GPS-13]. */
    private fun installId(dir: File): String =
        try {
            val f = File(dir, "install_id")
            val existing = if (f.exists()) f.readText().trim() else ""
            existing.ifEmpty {
                UUID.randomUUID().toString().also { f.writeText(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "install id unavailable", e)
            "unknown"
        }

    private companion object {
        const val TAG = "FieldLocationLogger"
        const val FLUSH_EVERY = 10
    }
}
