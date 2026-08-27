package com.example.nvhspectro.data

import com.example.nvhspectro.GpsStatus
import com.example.nvhspectro.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [S2, plan 3.6] Telemetry sidecar schema v2 + the v1 migration path — the
 * hand-rolled writer had no schema field, no escaping and no per-sample
 * monotonic stamps.
 */
class TelemetryCodecTest {

    private val samples = listOf(
        TelemetryData(
            speedKmh = 42.5f,
            accelerationG = 0.12f,
            latitude = 45.76,
            longitude = 4.83,
            altitude = 210.5,
            gpsStatus = GpsStatus.GOOD,
            speedAccuracyMs = 0.3f,
            elapsedRealtimeNanos = 123_456_789L
        ),
        TelemetryData(speedKmh = 44.0f, gpsStatus = GpsStatus.POOR)
    )

    @Test
    fun s2_v2RoundTrip_preservesEveryField() {
        val json = TelemetryCodec.encodeV2(
            folderName = "Essai_\"quoted\"", // escaping — would corrupt the v1 writer
            durationSec = 30,
            sampleRate = 44100,
            captureSource = "UNPROCESSED",
            appVersion = "13.2.0",
            samples = samples
        )
        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("\"appVersion\": \"13.2.0\""))

        val decoded = TelemetryCodec.decode(json)
        assertEquals(2, decoded.size)
        assertEquals(42.5f, decoded[0].speedKmh)
        assertEquals(210.5, decoded[0].altitude, 1e-9)
        assertEquals(0.3f, decoded[0].speedAccuracyMs)
        assertEquals(123_456_789L, decoded[0].elapsedRealtimeNanos)
        assertEquals(GpsStatus.GOOD, decoded[0].gpsStatus)
        assertEquals(GpsStatus.POOR, decoded[1].gpsStatus)
    }

    @Test
    fun s2_v1Sidecar_stillDecodes() {
        // Byte-for-byte shape of the historical hand-written format.
        val v1 = """
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
    }

    @Test
    fun s2_malformedJson_returnsEmptyInsteadOfBlockingTheImport() {
        assertTrue(TelemetryCodec.decode("{not json").isEmpty())
        assertTrue(TelemetryCodec.decode(null).isEmpty())
        assertTrue(TelemetryCodec.decode("").isEmpty())
    }
}
