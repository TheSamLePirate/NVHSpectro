package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [plan-gps GPS-0.4, Gate GPS-0] Round-trip proof for the v2 drive-trace codec. */
class FieldTraceV2Test {
    private val fullRecord =
        FieldTraceV2.Record(
            fixTimeNanos = 123_456_789_000L,
            callbackTimeNanos = 123_499_000_000L,
            utcTimeMs = 1_777_000_111_222L,
            provider = "gps",
            isMock = false,
            latitude = 45.7640431,
            longitude = 4.8356791,
            altitudeM = 172.4,
            rawSpeedMps = 13.72f,
            speedSigmaMps = 0.5f,
            horizontalAccuracyM = 3.9f,
            bearingDeg = 271.5f,
            estimatedSpeedMps = 13.68f,
            estimatedAccelMps2 = 0.42f,
            estimatedSpeedSigmaMps = 0.42f,
            validity = EstimateValidity.VALID,
            ageSinceFixNanos = 42_211_000L,
            rejection = null,
            nis = 1.37,
        )

    private val sparseRecord =
        FieldTraceV2.Record(
            fixTimeNanos = 200_000_000_000L,
            callbackTimeNanos = 200_050_000_000L,
            utcTimeMs = 1_777_000_222_333L,
            provider = "fused",
            isMock = true,
            latitude = -12.5,
            longitude = 130.25,
            altitudeM = null,
            rawSpeedMps = null,
            speedSigmaMps = null,
            horizontalAccuracyM = null,
            bearingDeg = null,
            estimatedSpeedMps = 0f,
            estimatedAccelMps2 = 0f,
            estimatedSpeedSigmaMps = null,
            validity = EstimateValidity.INVALID,
            ageSinceFixNanos = null,
            rejection = SampleRejection.NON_MONOTONIC_TIME,
        )

    private val metadata =
        FieldTraceV2.Metadata(
            schemaVersion = FieldTraceV2.SCHEMA_VERSION,
            installId = "3f2c9a4e-1111-2222-3333-444455556666",
            deviceModel = "Pixel 7 Pro",
        )

    @Test
    fun gps0_roundTrip_preservesEveryField() {
        val text =
            FieldTraceV2.encodeHeader(metadata) + "\n" +
                FieldTraceV2.encodeRow(fullRecord) + "\n" +
                FieldTraceV2.encodeRow(sparseRecord) + "\n"
        val trace = FieldTraceV2.parse(text)!!
        assertEquals(metadata, trace.metadata)
        assertEquals(listOf(fullRecord, sparseRecord), trace.records)
    }

    @Test
    fun gps0_headerModelWithSpaces_roundTrips() {
        val text = FieldTraceV2.encodeHeader(metadata)
        val trace = FieldTraceV2.parse(text)!!
        assertEquals("Pixel 7 Pro", trace.metadata.deviceModel)
        assertEquals(metadata.installId, trace.metadata.installId)
        assertTrue(trace.records.isEmpty())
    }

    @Test
    fun gps0_absentValues_areEmptyFieldsNeverNumericSentinels() {
        val line = FieldTraceV2.encodeRow(sparseRecord)
        // Gate GPS-0: no NaN markers, no 0-as-unknown — absence is emptiness.
        assertFalse("v1's NaN sentinel must be gone", line.contains("NaN"))
        val parsed = FieldTraceV2.parseRow(line)!!
        assertNull(parsed.rawSpeedMps)
        assertNull(parsed.speedSigmaMps)
        assertNull(parsed.altitudeM)
        assertNull(parsed.ageSinceFixNanos)
        assertEquals(SampleRejection.NON_MONOTONIC_TIME, parsed.rejection)
    }

    @Test
    fun gps0_truncatedOrMalformedRows_areSkippedNotFatal() {
        val goodRow = FieldTraceV2.encodeRow(fullRecord)
        val text =
            FieldTraceV2.encodeHeader(metadata) + "\n" +
                goodRow + "\n" +
                goodRow.substring(0, goodRow.length / 2) + "\n" + // process died mid-write
                "not,a,record\n"
        val trace = FieldTraceV2.parse(text)!!
        assertEquals(listOf(fullRecord), trace.records)
    }

    @Test
    fun gps2_legacyRowWithoutNisColumn_stillDecodes() {
        // Rows written before GPS-2 added `nis` lack the final column.
        val legacyLine = FieldTraceV2.encodeRow(sparseRecord).removeSuffix(",")
        val parsed = FieldTraceV2.parseRow(legacyLine)!!
        assertNull(parsed.nis)
        assertEquals(sparseRecord, parsed)
    }

    @Test
    fun gps0_nonV2Header_isRejected() {
        assertNull(FieldTraceV2.parse("elapsedRealtimeNanos,utcTimeMs,provider\n1,2,gps"))
        assertNull(FieldTraceV2.parse(""))
    }
}
