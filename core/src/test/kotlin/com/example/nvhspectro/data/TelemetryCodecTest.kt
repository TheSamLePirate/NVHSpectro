package com.example.nvhspectro.data

import com.example.nvhspectro.GpsStatus
import com.example.nvhspectro.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [S2, plan 3.6; plan-gps GPS-4.3] Telemetry sidecar schema v3 plus the
 * v1/v2 migration paths (Gate GPS-4: "migration v1 → v2 testée" extended
 * to v3). Older sidecars come back DEGRADED — "incertitude inconnue".
 */
class TelemetryCodecTest {
    private val samples =
        listOf(
            TelemetryData(
                speedKmh = 42.5f,
                accelerationG = 0.12f,
                latitude = 45.76,
                longitude = 4.83,
                altitude = 210.5,
                gpsStatus = GpsStatus.GOOD,
                speedAccuracyMs = 0.3f,
                elapsedRealtimeNanos = 123_456_789L,
                theoreticalSpeedKmh = 42.1f,
                theoreticalSpeedSigmaKmh = 1.6f,
                speedValidity = EstimateValidity.VALID,
            ),
            TelemetryData(speedKmh = 44.0f, gpsStatus = GpsStatus.POOR),
        )

    private val request =
        TelemetryCodec.EncodeRequest(
            folderName = "Essai_\"quoted\"", // escaping — would corrupt the v1 writer
            durationSec = 30,
            sampleRate = 44100,
            captureSource = "UNPROCESSED",
            appVersion = "13.2.0",
            speedEstimator = "kalman-va/1 Config(jerkPsd=0.5)",
        )

    @Test
    fun gps43_v3RoundTrip_preservesEstimateSigmaValidityAndAudioTime() {
        val json = TelemetryCodec.encodeV3(request, samples, audioTimesNanos = listOf(111L, 222L))
        assertTrue(json.contains("\"schemaVersion\": 3"))
        assertTrue(json.contains("\"speedStatus\": \"causale\""))

        val doc = TelemetryCodec.decodeDocument(json)
        assertEquals(2, doc.samples.size)
        val first = doc.samples[0]
        assertEquals(42.5f, first.speedKmh)
        assertEquals(210.5, first.altitude, 1e-9)
        assertEquals(0.3f, first.speedAccuracyMs)
        assertEquals(123_456_789L, first.elapsedRealtimeNanos)
        assertEquals(GpsStatus.GOOD, first.gpsStatus)
        assertEquals(42.1f, first.theoreticalSpeedKmh)
        assertEquals(1.6f, first.theoreticalSpeedSigmaKmh)
        assertEquals(EstimateValidity.VALID, first.speedValidity)
        // No σ on the second sample: null survives the trip, never 0-as-unknown.
        assertNull(doc.samples[1].theoreticalSpeedSigmaKmh)
        assertEquals(listOf(111L, 222L), doc.audioTimesNanos)
        assertEquals(request.speedEstimator, doc.speedEstimator)
        assertEquals(TelemetryCodec.SPEED_STATUS_CAUSAL, doc.speedStatus)
    }

    @Test
    fun gps43_v2Sidecar_decodesAsDegradedWithUnknownSigma() {
        // Byte-for-byte shape of a Phase-3 (schema v2) sidecar.
        val v2 =
            """
            {
              "schemaVersion": 2,
              "appVersion": "13.2.0",
              "folderName": "Essai",
              "durationSec": 8,
              "sampleRate": 44100,
              "captureSource": "VOICE_RECOGNITION",
              "telemetryCount": 1,
              "telemetryData": [
                {"index": 0, "speedKmh": 12.5, "accelerationG": 0.05, "lat": 45.7, "lng": 4.8,
                 "altitude": 200.0, "gpsStatus": "GOOD", "speedAccuracyMs": 0.5,
                 "elapsedRealtimeNanos": 777}
              ]
            }
            """.trimIndent()
        val doc = TelemetryCodec.decodeDocument(v2)
        assertEquals(1, doc.samples.size)
        assertEquals(12.5f, doc.samples[0].speedKmh)
        assertEquals(777L, doc.samples[0].elapsedRealtimeNanos)
        // Unknown uncertainty, not INVALID: legacy analyses stay usable.
        assertEquals(EstimateValidity.DEGRADED, doc.samples[0].speedValidity)
        assertNull(doc.samples[0].theoreticalSpeedSigmaKmh)
        assertNull(doc.audioTimesNanos)
        assertNull(doc.speedEstimator)
    }

    @Test
    fun s2_v1Sidecar_stillDecodes() {
        // Byte-for-byte shape of the historical hand-written format.
        val v1 =
            """
            {
              "folderName": "Essai_2026-08-26_21h36m23s001",
              "durationSec": 6,
              "sampleRate": 44100,
              "captureSource": "VOICE_RECOGNITION",
              "telemetryCount": 2,
              "telemetryData": [
                {"index": 0, "speedKmh": 12.5, "accelerationG": 0.05, "lat": 45.7, "lng": 4.8, "gpsStatus": "GOOD"},
                {"index": 1, "speedKmh": 13.0, "accelerationG": 0.06, "lat": 45.71, "lng": 4.81, "gpsStatus": "NONE"}
              ]
            }
            """.trimIndent()
        val decoded = TelemetryCodec.decode(v1)
        assertEquals(2, decoded.size)
        assertEquals(12.5f, decoded[0].speedKmh)
        assertEquals(GpsStatus.GOOD, decoded[0].gpsStatus)
        assertEquals(GpsStatus.NONE, decoded[1].gpsStatus)
        assertEquals("v1 has no monotonic stamps", 0L, decoded[0].elapsedRealtimeNanos)
        assertEquals(EstimateValidity.DEGRADED, decoded[0].speedValidity)
    }

    @Test
    fun s2_malformedJson_returnsEmptyInsteadOfBlockingTheImport() {
        assertTrue(TelemetryCodec.decode("{not json").isEmpty())
        assertTrue(TelemetryCodec.decode(null).isEmpty())
        assertTrue(TelemetryCodec.decode("").isEmpty())
    }
}
