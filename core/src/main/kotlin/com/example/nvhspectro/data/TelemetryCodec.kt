package com.example.nvhspectro.data

import com.example.nvhspectro.GpsStatus
import com.example.nvhspectro.TelemetryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [S2, plan 3.6; plan-gps GPS-4.3] The telemetry sidecar format —
 * kotlinx-serialization replaces the historical string-concatenation writer.
 *
 * Schema v2 added `schemaVersion`, `appVersion`, per-sample monotonic
 * `elapsedRealtimeNanos`, `altitude` and `speedAccuracyMs`. Schema v3 makes
 * the sidecar metrologically complete [GPS-09/13/14 surfaces]: per sample the
 * ESTIMATED speed with its 1-σ and validity plus the paired audio-frame
 * BOOTTIME; per document the estimator identity/parameters, the capture-time
 * speed status ("causale" — deferred RTS smoothing happens at analysis, not
 * capture [GPS-4.4]) and the order-search confidence level [GPS-4.2].
 *
 * The reader decodes every version: v3 restores σ/validity; v1/v2 sidecars
 * carry no uncertainty, so their estimates come back DEGRADED (usable,
 * "incertitude inconnue") with σ = null — never 0-as-unknown.
 */
object TelemetryCodec {
    const val SCHEMA_VERSION = 3

    /** Capture-time speed status recorded in v3 documents [GPS-4.4 labeling]. */
    const val SPEED_STATUS_CAUSAL = "causale"

    @Serializable
    data class SampleV3(
        val index: Int,
        val speedKmh: Float,
        val accelerationG: Float,
        val lat: Double,
        val lng: Double,
        val altitude: Double = 0.0,
        val gpsStatus: String = "NONE",
        val speedAccuracyMs: Float = 0f,
        val elapsedRealtimeNanos: Long = 0L,
        /** [GPS-4.3] Estimated (Kalman) speed at this frame, km/h. */
        val estSpeedKmh: Float = 0f,
        /** 1-σ of estSpeedKmh, km/h; null = estimator carried no covariance. */
        val estSpeedSigmaKmh: Float? = null,
        /** EstimateValidity name at this frame. */
        val validity: String = EstimateValidity.DEGRADED.name,
        /** BOOTTIME of the paired audio window's center sample [GPS-03]. */
        val audioTimeNanos: Long = 0L,
    )

    @Serializable
    data class DocumentV3(
        val schemaVersion: Int = SCHEMA_VERSION,
        val appVersion: String = "",
        val folderName: String = "",
        val durationSec: Int = 0,
        val sampleRate: Int = 0,
        val captureSource: String = "",
        /** [GPS-4.3] Estimator identity + parameters that produced estSpeedKmh. */
        val speedEstimator: String = "",
        /** "causale" at capture; deferred analyses relabel their own outputs. */
        val speedStatus: String = SPEED_STATUS_CAUSAL,
        /** k of the k·σf order-search band [GPS-4.2]. */
        val orderConfidenceK: Double = OrderSearchPolicy.CONFIDENCE_K,
        val telemetryCount: Int = 0,
        val telemetryData: List<SampleV3> = emptyList(),
    )

    /** Everything a deferred analysis needs from a sidecar [GPS-4.4]. */
    class DecodedTelemetry(
        val samples: List<TelemetryData>,
        /** Per-sample audio BOOTTIME (v3 only); null on v1/v2 sidecars. */
        val audioTimesNanos: List<Long>?,
        val speedEstimator: String?,
        val speedStatus: String?,
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

    class EncodeRequest(
        val folderName: String,
        val durationSec: Int,
        val sampleRate: Int,
        val captureSource: String,
        val appVersion: String,
        val speedEstimator: String,
    )

    /** Write a v3 sidecar. [audioTimesNanos] pairs 1:1 with [samples] (or empty). */
    fun encodeV3(
        request: EncodeRequest,
        samples: List<TelemetryData>,
        audioTimesNanos: List<Long>,
    ): String {
        val doc =
            DocumentV3(
                appVersion = request.appVersion,
                folderName = request.folderName,
                durationSec = request.durationSec,
                sampleRate = request.sampleRate,
                captureSource = request.captureSource,
                speedEstimator = request.speedEstimator,
                telemetryCount = samples.size,
                telemetryData =
                    samples.mapIndexed { idx, t ->
                        SampleV3(
                            index = idx,
                            speedKmh = t.speedKmh,
                            accelerationG = t.accelerationG,
                            lat = t.latitude,
                            lng = t.longitude,
                            altitude = t.altitude,
                            gpsStatus = t.gpsStatus.name,
                            speedAccuracyMs = t.speedAccuracyMs,
                            elapsedRealtimeNanos = t.elapsedRealtimeNanos,
                            estSpeedKmh = t.theoreticalSpeedKmh,
                            estSpeedSigmaKmh = t.theoreticalSpeedSigmaKmh,
                            validity = t.speedValidity.name,
                            audioTimeNanos = audioTimesNanos.getOrElse(idx) { 0L },
                        )
                    },
            )
        return json.encodeToString(DocumentV3.serializer(), doc)
    }

    /** Legacy-shaped entry point kept for existing import paths. */
    fun decode(jsonText: String?): List<TelemetryData> = decodeDocument(jsonText).samples

    /**
     * Decode a sidecar of any schema. Returns empty samples on malformed
     * input — telemetry is optional and must never block an audio import.
     */
    fun decodeDocument(jsonText: String?): DecodedTelemetry {
        if (jsonText.isNullOrBlank()) return DecodedTelemetry(emptyList(), null, null, null)
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            when {
                version >= SCHEMA_VERSION -> decodeV3(root)
                version == 2 -> decodeV2(root)
                else -> DecodedTelemetry(decodeV1(root), null, null, null)
            }
        } catch (e: Exception) {
            DecodedTelemetry(emptyList(), null, null, null)
        }
    }

    private fun decodeV3(root: kotlinx.serialization.json.JsonObject): DecodedTelemetry {
        val doc = json.decodeFromJsonElement(DocumentV3.serializer(), root)
        val samples =
            doc.telemetryData.map { s ->
                TelemetryData(
                    speedKmh = s.speedKmh,
                    accelerationG = s.accelerationG,
                    latitude = s.lat,
                    longitude = s.lng,
                    altitude = s.altitude,
                    gpsStatus = gpsStatusOf(s.gpsStatus),
                    speedAccuracyMs = s.speedAccuracyMs,
                    elapsedRealtimeNanos = s.elapsedRealtimeNanos,
                    theoreticalSpeedKmh = s.estSpeedKmh,
                    theoreticalSpeedSigmaKmh = s.estSpeedSigmaKmh,
                    speedValidity = validityOf(s.validity),
                )
            }
        return DecodedTelemetry(
            samples = samples,
            audioTimesNanos = doc.telemetryData.map { it.audioTimeNanos },
            speedEstimator = doc.speedEstimator.ifEmpty { null },
            speedStatus = doc.speedStatus.ifEmpty { null },
        )
    }

    /** v2: no estimator fields — unknown uncertainty comes back DEGRADED, σ null. */
    private fun decodeV2(root: kotlinx.serialization.json.JsonObject): DecodedTelemetry {
        val doc = json.decodeFromJsonElement(DocumentV3.serializer(), root)
        val samples =
            doc.telemetryData.map { s ->
                TelemetryData(
                    speedKmh = s.speedKmh,
                    accelerationG = s.accelerationG,
                    latitude = s.lat,
                    longitude = s.lng,
                    altitude = s.altitude,
                    gpsStatus = gpsStatusOf(s.gpsStatus),
                    speedAccuracyMs = s.speedAccuracyMs,
                    elapsedRealtimeNanos = s.elapsedRealtimeNanos,
                    speedValidity = EstimateValidity.DEGRADED,
                )
            }
        return DecodedTelemetry(samples, null, null, null)
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
                    gpsStatus = gpsStatusOf(obj["gpsStatus"]?.jsonPrimitive?.content ?: "GOOD"),
                    speedValidity = EstimateValidity.DEGRADED,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun gpsStatusOf(name: String): GpsStatus =
        when (name) {
            "POOR" -> GpsStatus.POOR
            "NONE" -> GpsStatus.NONE
            else -> GpsStatus.GOOD
        }

    private fun validityOf(name: String): EstimateValidity =
        try {
            EstimateValidity.valueOf(name)
        } catch (_: IllegalArgumentException) {
            EstimateValidity.DEGRADED
        }
}
