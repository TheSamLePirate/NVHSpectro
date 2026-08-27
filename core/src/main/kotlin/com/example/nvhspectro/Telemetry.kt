package com.example.nvhspectro

import com.example.nvhspectro.data.EstimateValidity

/** GPS quality for the UI LED — driven by SPEED accuracy where available [audit G3]. */
enum class GpsStatus {
    NONE, // Rouge : pas de fix exploitable
    POOR, // Orange : précision moyenne
    GOOD, // Vert : bonne précision
}

data class TelemetryData(
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accelerationG: Float = 0f,
    val ttnrDb: Float = 0f,
    val trackedOrderDbFS: Double = -120.0,
    val trackedOrderEmergenceDb: Double = 0.0,
    val gpsStatus: GpsStatus = GpsStatus.NONE,
    /**
     * Wall-clock stamp for display/export labeling ONLY. Never use for interval
     * math — that lives on elapsedRealtimeNanos inside SpeedProvider [audit G1].
     */
    val timestampMs: Long = System.currentTimeMillis(),
    val theoreticalSpeedKmh: Float = 0f,
    /** 1-σ Doppler speed error of the underlying fix (0 = unknown) [G3, S2]. */
    val speedAccuracyMs: Float = 0f,
    /** Monotonic stamp of the underlying fix (0 = none) — telemetry schema v2 [S2]. */
    val elapsedRealtimeNanos: Long = 0L,
    /**
     * [GPS-1.1, GPS-09] Validity of [theoreticalSpeedKmh]. INVALID means the
     * numeric value is diagnostic only: the kinematic chain must not consume
     * it and the UI shows "--" instead of a number. Sidecar export of this
     * field lands with schema v3 (plan-gps GPS-4.3).
     */
    val speedValidity: EstimateValidity = EstimateValidity.INVALID,
)
