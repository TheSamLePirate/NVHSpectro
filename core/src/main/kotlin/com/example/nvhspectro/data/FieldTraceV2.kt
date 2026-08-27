package com.example.nvhspectro.data

/**
 * [plan-gps GPS-0.4, GPS-13] Drive-trace schema v2 — the pure codec behind the
 * debug FieldLocationLogger.
 *
 * Schema v1 recorded raw Location fields only and used NaN as its absence
 * marker. v2 adds the callback delivery time, the estimator's outcome per fix
 * (state, validity, rejection), and an anonymized device identity in the
 * header — exactly the data the GPS-2 Kalman is tuned and validated against.
 * Absence is an EMPTY CSV field, never a numeric sentinel (Gate GPS-0).
 *
 * Units and time bases [GPS-0.5]: `*Nanos` columns are BOOTTIME
 * (elapsedRealtimeNanos); `utcTimeMs` is `Location.time`, kept for human
 * labeling only — never for interval math [audit G1]. Speeds m/s,
 * accelerations m/s², distances meters, bearings degrees.
 */
object FieldTraceV2 {
    const val SCHEMA_VERSION = 2

    const val HEADER_PREFIX = "# nvh-field-trace v2 "

    /** `model=` is last on the header line: device models may contain spaces. */
    data class Metadata(
        val schemaVersion: Int,
        /** Random per-install UUID — anonymized device identity [GPS-13]. */
        val installId: String,
        val deviceModel: String,
    )

    /** One raw fix + the estimator's outcome for it. Null = value absent. */
    data class Record(
        val fixTimeNanos: Long,
        val callbackTimeNanos: Long,
        val utcTimeMs: Long,
        val provider: String,
        val isMock: Boolean,
        val latitude: Double,
        val longitude: Double,
        val altitudeM: Double?,
        val rawSpeedMps: Float?,
        val speedSigmaMps: Float?,
        val horizontalAccuracyM: Float?,
        val bearingDeg: Float?,
        val estimatedSpeedMps: Float,
        val estimatedAccelMps2: Float,
        val estimatedSpeedSigmaMps: Float?,
        val validity: EstimateValidity,
        val ageSinceFixNanos: Long?,
        /** Null = the fix fed the estimator normally, or produced no sample at all. */
        val rejection: SampleRejection?,
    )

    data class Trace(
        val metadata: Metadata,
        val records: List<Record>,
    )

    private const val COLUMNS =
        "fixTimeNanos,callbackTimeNanos,utcTimeMs,provider,isMock," +
            "lat,lon,altM,speedMps,speedSigmaMps,horizAccM,bearingDeg," +
            "estSpeedMps,estAccelMps2,estSpeedSigmaMps,validity,ageSinceFixNanos,rejection"
    private val FIELD_COUNT = COLUMNS.count { it == ',' } + 1

    fun encodeHeader(metadata: Metadata): String = headerLine(metadata) + "\n" + COLUMNS

    private fun headerLine(m: Metadata): String = "$HEADER_PREFIX install=${m.installId} model=${m.deviceModel}"

    /** Locale-independent (Kotlin toString: '.' decimals); null → empty field. */
    fun encodeRow(r: Record): String =
        listOf(
            r.fixTimeNanos.toString(),
            r.callbackTimeNanos.toString(),
            r.utcTimeMs.toString(),
            r.provider.replace(',', '_'),
            r.isMock.toString(),
            r.latitude.toString(),
            r.longitude.toString(),
            r.altitudeM?.toString().orEmpty(),
            r.rawSpeedMps?.toString().orEmpty(),
            r.speedSigmaMps?.toString().orEmpty(),
            r.horizontalAccuracyM?.toString().orEmpty(),
            r.bearingDeg?.toString().orEmpty(),
            r.estimatedSpeedMps.toString(),
            r.estimatedAccelMps2.toString(),
            r.estimatedSpeedSigmaMps?.toString().orEmpty(),
            r.validity.name,
            r.ageSinceFixNanos?.toString().orEmpty(),
            r.rejection?.name.orEmpty(),
        ).joinToString(",")

    /** Whole-file decode. Returns null only when the v2 header is missing. */
    fun parse(text: String): Trace? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()
        if (header == null || !header.startsWith(HEADER_PREFIX)) return null
        val rest = header.removePrefix(HEADER_PREFIX)
        val metadata =
            Metadata(
                schemaVersion = SCHEMA_VERSION,
                installId = rest.substringAfter("install=").substringBefore(" "),
                deviceModel = rest.substringAfter("model=", ""),
            )
        val records =
            lines
                .drop(1)
                .filterNot { it == COLUMNS }
                .mapNotNull(::parseRow)
        return Trace(metadata, records)
    }

    /** Malformed rows decode to null and are skipped — a trace can end mid-line on process death. */
    fun parseRow(line: String): Record? {
        val fields = line.split(',')
        if (fields.size != FIELD_COUNT) return null
        // Named arguments evaluate in source order, which is column order.
        val f = fields.iterator()
        return try {
            Record(
                fixTimeNanos = f.next().toLong(),
                callbackTimeNanos = f.next().toLong(),
                utcTimeMs = f.next().toLong(),
                provider = f.next(),
                isMock = f.next().toBooleanStrict(),
                latitude = f.next().toDouble(),
                longitude = f.next().toDouble(),
                altitudeM = f.next().ifEmpty { null }?.toDouble(),
                rawSpeedMps = f.next().ifEmpty { null }?.toFloat(),
                speedSigmaMps = f.next().ifEmpty { null }?.toFloat(),
                horizontalAccuracyM = f.next().ifEmpty { null }?.toFloat(),
                bearingDeg = f.next().ifEmpty { null }?.toFloat(),
                estimatedSpeedMps = f.next().toFloat(),
                estimatedAccelMps2 = f.next().toFloat(),
                estimatedSpeedSigmaMps = f.next().ifEmpty { null }?.toFloat(),
                validity = EstimateValidity.valueOf(f.next()),
                ageSinceFixNanos = f.next().ifEmpty { null }?.toLong(),
                rejection = f.next().ifEmpty { null }?.let(SampleRejection::valueOf),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
