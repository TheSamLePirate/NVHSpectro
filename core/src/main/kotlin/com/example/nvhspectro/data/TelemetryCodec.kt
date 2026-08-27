package com.example.nvhspectro.data

import com.example.nvhspectro.GpsStatus
import com.example.nvhspectro.TelemetryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [S2, plan 3.6] The telemetry sidecar format — kotlinx-serialization replaces
 * the historical string-concatenation writer (no escaping, no schema field).
 *
 * Schema v2 adds `schemaVersion`, `appVersion`, per-sample monotonic
 * `elapsedRealtimeNanos`, `altitude` and `speedAccuracyMs`. The reader
 * still decodes v1 sidecars (no schemaVersion; index/speed/accel/lat/lng/
 * gpsStatus only) — existing recordings keep loading.
 */
object TelemetryCodec {

    const val SCHEMA_VERSION = 2

    @Serializable
    data class SampleV2(
        val index: Int,
        val speedKmh: Float,
        val accelerationG: Float,
        val lat: Double,
        val lng: Double,
        val altitude: Double = 0.0,
        val gpsStatus: String = "NONE",
        val speedAccuracyMs: Float = 0f,
        val elapsedRealtimeNanos: Long = 0L
    )

    @Serializable
    data class DocumentV2(
        val schemaVersion: Int = SCHEMA_VERSION,
        val appVersion: String = "",
        val folderName: String = "",
        val durationSec: Int = 0,
        val sampleRate: Int = 0,
        val captureSource: String = "",
        val telemetryCount: Int = 0,
        val telemetryData: List<SampleV2> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encodeV2(
        folderName: String,
        durationSec: Int,
        sampleRate: Int,
        captureSource: String,
        appVersion: String,
        samples: List<TelemetryData>
    ): String {
        val doc = DocumentV2(
            appVersion = appVersion,
            folderName = folderName,
            durationSec = durationSec,
            sampleRate = sampleRate,
            captureSource = captureSource,
            telemetryCount = samples.size,
            telemetryData = samples.mapIndexed { idx, t ->
                SampleV2(
                    index = idx,
                    speedKmh = t.speedKmh,
                    accelerationG = t.accelerationG,
                    lat = t.latitude,
                    lng = t.longitude,
                    altitude = t.altitude,
                    gpsStatus = t.gpsStatus.name,
                    speedAccuracyMs = t.speedAccuracyMs,
                    elapsedRealtimeNanos = t.elapsedRealtimeNanos
                )
            }
        )
        return json.encodeToString(DocumentV2.serializer(), doc)
    }

    /**
     * Decode a sidecar of either schema. Returns an empty list on malformed
     * input — telemetry is optional and must never block an audio import.
     */
    fun decode(jsonText: String?): List<TelemetryData> {
        if (jsonText.isNullOrBlank()) return emptyList()
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            if (version >= 2) {
                json.decodeFromJsonElement(DocumentV2.serializer(), root).telemetryData.map { s ->
                    TelemetryData(
                        speedKmh = s.speedKmh,
                        accelerationG = s.accelerationG,
                        latitude = s.lat,
                        longitude = s.lng,
                        altitude = s.altitude,
                        gpsStatus = gpsStatusOf(s.gpsStatus),
                        speedAccuracyMs = s.speedAccuracyMs,
                        elapsedRealtimeNanos = s.elapsedRealtimeNanos
                    )
                }
            } else {
                decodeV1(root)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** v1: hand-written JSON with index/speedKmh/accelerationG/lat/lng/gpsStatus. */
    private fun decodeV1(root: kotlinx.serialization.json.JsonObject): List<TelemetryData> {
        val arr = root["telemetryData"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                TelemetryData(
                    speedKmh = obj["speedKmh"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    accelerationG = obj["accelerationG"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    latitude = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    longitude = obj["lng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    gpsStatus = gpsStatusOf(obj["gpsStatus"]?.jsonPrimitive?.content ?: "GOOD")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun gpsStatusOf(name: String): GpsStatus = when (name) {
        "POOR" -> GpsStatus.POOR
        "NONE" -> GpsStatus.NONE
        else -> GpsStatus.GOOD
    }
}
