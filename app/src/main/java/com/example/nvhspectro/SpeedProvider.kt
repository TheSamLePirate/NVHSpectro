package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.nvhspectro.data.AlphaBetaSpeedEstimator
import com.example.nvhspectro.data.FieldLocationLogger
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GNSS speed acquisition, reworked per audit §3b [G1–G4, plan 2.4]:
 *
 * - GNSS-provenance first: subscribes to LocationManager GPS_PROVIDER (raw
 *   Doppler speed); the fused provider is only a fallback when GPS is off,
 *   and its fixes feed the estimator only when they are GNSS-sourced [G2].
 * - All interval/staleness math on monotonic elapsedRealtimeNanos [G1].
 * - The quality LED is driven by speedAccuracyMetersPerSecond — the error of
 *   the quantity this app actually lives on — with the horizontal-accuracy
 *   proxy only as a pre-API-26 fallback [G3].
 * - An α-β estimator smooths Doppler speed and supplies per-frame predicted
 *   speed + acceleration [G4] — this is what removed the 1.2 s display
 *   latency [L5] and the wall-clock derivative spikes.
 *
 * Runs only while started — LIVE mode only [C7]. The old always-on
 * full-tracking GnssMeasurements registration (empty callback, battery-only
 * cost) is not carried over — audit G5 / decision D8 recommendation.
 */
class SpeedProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val fieldLogger: FieldLocationLogger? =
        if (BuildConfig.DEBUG) FieldLocationLogger(appContext) else null

    private val lock = Any()
    private val estimator = AlphaBetaSpeedEstimator()
    private var lastFix: Location? = null
    private var lastFixElapsedNanos = Long.MIN_VALUE
    private var started = false

    private val _telemetry = MutableStateFlow(TelemetryData())

    /** ~1 Hz updates for the UI card; per-frame consumers use [currentTelemetry]. */
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location, gnssSourced = true)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                // [G2] Provenance filter: only GNSS-sourced fused fixes carry Doppler speed.
                onFix(loc, gnssSourced = loc.provider == LocationManager.GPS_PROVIDER)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f, gpsListener, Looper.getMainLooper()
                )
            } else {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                    .setMinUpdateIntervalMillis(200L)
                    .build()
                fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            Log.w(TAG, "location subscription failed", e)
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            locationManager.removeUpdates(gpsListener)
        } catch (e: Exception) {
            Log.w(TAG, "gps unsubscribe failed", e)
        }
        try {
            fusedClient.removeLocationUpdates(fusedCallback)
        } catch (e: Exception) {
            Log.w(TAG, "fused unsubscribe failed", e)
        }
    }

    /**
     * Thread-safe snapshot with speed PREDICTED to now — called from the DSP
     * thread at frame rate. Prediction is what makes the live H1 projection
     * current instead of 1.2 s late.
     */
    fun currentTelemetry(): TelemetryData = synchronized(lock) {
        buildTelemetry(SystemClock.elapsedRealtimeNanos())
    }

    fun reset() = synchronized(lock) {
        estimator.reset()
        lastFix = null
        lastFixElapsedNanos = Long.MIN_VALUE
        _telemetry.value = TelemetryData()
    }

    private fun onFix(loc: Location, gnssSourced: Boolean) {
        fieldLogger?.log(loc)
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        synchronized(lock) {
            lastFix = loc
            lastFixElapsedNanos = loc.elapsedRealtimeNanos
            if (gnssSourced && loc.hasSpeed()) {
                // [G1] Monotonic fix timestamp, never loc.time (steppable UTC).
                estimator.update(loc.elapsedRealtimeNanos, loc.speed)
            }
            _telemetry.value = buildTelemetry(nowNanos)
        }
    }

    /** Call under [lock]. */
    private fun buildTelemetry(nowNanos: Long): TelemetryData {
        val fix = lastFix
        return TelemetryData(
            speedKmh = (fix?.takeIf { it.hasSpeed() }?.speed ?: 0f) * 3.6f,
            theoreticalSpeedKmh = estimator.predictAt(nowNanos) * 3.6f,
            accelerationG = estimator.accelMps2 / 9.81f,
            altitude = fix?.takeIf { it.hasAltitude() }?.altitude ?: 0.0,
            latitude = fix?.latitude ?: 0.0,
            longitude = fix?.longitude ?: 0.0,
            gpsStatus = qualityOf(fix, nowNanos)
        )
    }

    /** [G3] LED keyed to speed accuracy — the error term the app lives on. */
    private fun qualityOf(fix: Location?, nowNanos: Long): GpsStatus {
        fix ?: return GpsStatus.NONE
        if (nowNanos - lastFixElapsedNanos > STALE_FIX_NANOS) return GpsStatus.NONE
        if (Build.VERSION.SDK_INT >= 26 && fix.hasSpeedAccuracy()) {
            val a = fix.speedAccuracyMetersPerSecond
            return when {
                a <= 0.5f -> GpsStatus.GOOD
                a <= 1.5f -> GpsStatus.POOR
                else -> GpsStatus.NONE
            }
        }
        val h = if (fix.hasAccuracy()) fix.accuracy else 999f
        return when {
            h <= 10f -> GpsStatus.GOOD
            h <= 30f -> GpsStatus.POOR
            else -> GpsStatus.NONE
        }
    }

    private companion object {
        const val TAG = "SpeedProvider"
        const val STALE_FIX_NANOS = 5_000_000_000L
    }
}
