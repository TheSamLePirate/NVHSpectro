package com.example.nvhspectro

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.example.nvhspectro.data.DiagnosticLog
import com.example.nvhspectro.data.EstimatorOutcome
import com.example.nvhspectro.data.FieldLocationLogger
import com.example.nvhspectro.data.GnssSpeedSample
import com.example.nvhspectro.data.GnssSpeedSession
import com.example.nvhspectro.data.SpeedSampleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/**
 * GNSS speed acquisition [audit §3b G1–G4, plan 2.4; plan-gps GPS-1/GPS-3]:
 *
 * - GPS_PROVIDER is the ONLY metrological source [G2, GPS-07]: its listener is
 *   registered even while the provider is disabled (so enable/disable state
 *   changes arrive mid-session [GPS-3.2]); on API 31+ the subscription is an
 *   explicit high-accuracy, zero-interval, unbatched LocationRequest
 *   [GPS-3.1]. The fallback provider runs only while GPS is off and is
 *   INFORMATION_ONLY — its fixes never feed the estimator unless their
 *   provider field claims GNSS provenance.
 * - All callbacks are delivered on the dedicated "nvh-gnss" thread, never on
 *   main [GPS-3.1]; delivery latency (callback − fix time) is in every trace
 *   row [GPS-13].
 * - The estimator behind [GnssSpeedSession] (Kalman since GPS-2) enforces
 *   qualification and validity; [telemetryAt] evaluates it at the audio
 *   capture instant [GPS-03].
 * - [GnssDiagnosticsMonitor] snapshots signal quality for traces and owns the
 *   full-tracking A/B switch and capability matrix [GPS-3.3/3.4/3.5].
 * - Runs only while started — LIVE mode only [C7]; start()/stop() reset the
 *   speed session [GPS-08]; stop() releases every GNSS resource.
 *
 * Units and time bases [GPS-0.5]: estimator I/O is m/s and BOOTTIME
 * nanoseconds; TelemetryData speeds are km/h for display. Location.time (UTC)
 * is logged for labeling only — it never enters interval math [G1].
 */
class SpeedProvider(
    context: Context,
    /** [GPS-3.4] A/B switch for the GPS-5 campaign; default OFF until proven. */
    fullTrackingRequested: Boolean = false,
    /** User-facing state messages (provider off, permission missing) [GPS-3.2]. */
    private val onNotice: ((String) -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // [GPS-3.1] GNSS callbacks live on their own thread, never on main.
    private val gnssThread = HandlerThread("nvh-gnss").apply { start() }
    private val gnssHandler = Handler(gnssThread.looper)
    private val gnssExecutor = Executor { gnssHandler.post(it) }

    private val diagnostics =
        GnssDiagnosticsMonitor(locationManager, gnssHandler, gnssExecutor, fullTrackingRequested)

    private val fieldLogger: FieldLocationLogger? =
        if (BuildConfig.DEBUG) FieldLocationLogger(appContext, diagnostics.capabilitiesLine()) else null

    private val lock = Any()

    // [GPS-1.1/1.3] Qualification + validity enforcement around the estimator.
    private val session = GnssSpeedSession()
    private var lastFix: Location? = null
    private var lastFixElapsedNanos = Long.MIN_VALUE
    private var started = false
    private var fusedFallbackActive = false

    private val _telemetry = MutableStateFlow(TelemetryData())

    /** ~1 Hz updates for the UI card; per-frame consumers use [telemetryAt]. */
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    /** [GPS-4.3] Estimator identity + parameters, stamped into exports. */
    val estimatorDescription: String get() = session.description

    private val gpsListener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location, source = SpeedSampleSource.GPS)

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit

            override fun onProviderEnabled(provider: String) {
                onNotice?.invoke("📡 GPS réactivé — acquisition en cours")
                setFusedFallback(false)
            }

            override fun onProviderDisabled(provider: String) {
                if (!started) return
                onNotice?.invoke("📡 GPS désactivé — vitesse GNSS indisponible")
                // [GPS-3.2 gate] No ghost values: the session forgets the old
                // speed immediately instead of waiting for the horizon to expire.
                reset()
                setFusedFallback(true)
            }
        }

    private val fusedListener =
        LocationListener { loc ->
            // [G2, GPS-3.2] INFORMATION_ONLY unless the fix claims GNSS provenance.
            val source =
                if (loc.provider == LocationManager.GPS_PROVIDER) {
                    SpeedSampleSource.FUSED_GNSS
                } else {
                    null // no sample: the fix must not feed the estimator
                }
            onFix(loc, source)
        }

    fun start() {
        if (started) return
        started = true
        // [GPS-08] A LIVE re-entry is a NEW speed session: no speed from the
        // previous session may be served before the first fresh fix.
        reset()
        try {
            subscribeGps()
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onNotice?.invoke("📡 GPS désactivé — vitesse GNSS indisponible")
                setFusedFallback(true)
            }
            diagnostics.register()
        } catch (e: SecurityException) {
            // [GPS-12, GPS-3.2] Approximate-only permission cannot feed a
            // metrological speed chain — say so instead of silently degrading.
            DiagnosticLog.w(TAG, "location permission missing", e)
            onNotice?.invoke("⚠️ Localisation précise requise pour la vitesse GNSS")
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { locationManager.removeUpdates(gpsListener) }
        setFusedFallback(false)
        diagnostics.unregister()
        // [GPS-08] Leaving LIVE ends the speed session as well.
        reset()
    }

    /** Full release (ViewModel death): stop + quit the GNSS thread [L1]. */
    fun shutdown() {
        stop()
        gnssThread.quitSafely()
    }

    /** Thread-safe snapshot with speed PREDICTED to now — for UI-driven reads. */
    fun currentTelemetry(): TelemetryData =
        synchronized(lock) {
            buildTelemetry(SystemClock.elapsedRealtimeNanos())
        }

    /**
     * [GPS-03] Estimate evaluated at an explicit BOOTTIME instant — the DSP
     * consumer passes the AUDIO CAPTURE time of the frame being analyzed, so
     * a backlogged DSP queue can no longer pair a spectrum with a speed newer
     * than the sound.
     */
    fun telemetryAt(elapsedRealtimeNanos: Long): TelemetryData =
        synchronized(lock) {
            buildTelemetry(elapsedRealtimeNanos)
        }

    fun reset() =
        synchronized(lock) {
            session.reset()
            lastFix = null
            lastFixElapsedNanos = Long.MIN_VALUE
            _telemetry.value = TelemetryData()
        }

    /**
     * [GPS-3.1] Registered even while the provider is disabled: fixes start
     * flowing the moment the user re-enables GPS, and enable/disable state
     * changes reach [gpsListener] mid-session [GPS-3.2].
     */
    @SuppressLint("MissingPermission")
    private fun subscribeGps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request =
                LocationRequest
                    .Builder(0L) // 0 = the chipset's own max cadence
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateDistanceMeters(0f)
                    .setMaxUpdateDelayMillis(0L) // no batching — measurement mode
                    .build()
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                request,
                gnssExecutor,
                gpsListener,
            )
        } else {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                gpsListener,
                gnssThread.looper,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun setFusedFallback(active: Boolean) {
        if (active == fusedFallbackActive) return
        fusedFallbackActive = active
        if (!active) {
            runCatching { locationManager.removeUpdates(fusedListener) }
            return
        }
        val provider =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationManager.FUSED_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                FUSED_FALLBACK_INTERVAL_MS,
                0f,
                fusedListener,
                gnssThread.looper,
            )
        }.onFailure { DiagnosticLog.w(TAG, "fallback provider unavailable", it) }
    }

    private fun onFix(
        loc: Location,
        source: SpeedSampleSource?,
    ) {
        val callbackNanos = SystemClock.elapsedRealtimeNanos()
        val mock = isMockFix(loc)
        // [GPS-0.1] The qualified sample: only a GNSS-sourced fix carrying a
        // Doppler speed feeds the estimator [G2]; σv rides along and weights
        // the Kalman update [GPS-02].
        val sample =
            if (source != null && loc.hasSpeed()) {
                GnssSpeedSample(
                    // [G1] Monotonic fix timestamp, never loc.time (steppable UTC).
                    fixTimeNanos = loc.elapsedRealtimeNanos,
                    callbackTimeNanos = callbackNanos,
                    speedMps = loc.speed,
                    speedSigmaMps =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasSpeedAccuracy()) {
                            loc.speedAccuracyMetersPerSecond
                        } else {
                            null
                        },
                    source = source,
                    isMock = mock,
                )
            } else {
                null
            }
        synchronized(lock) {
            lastFix = loc
            lastFixElapsedNanos = loc.elapsedRealtimeNanos
            // [GPS-1.3] The session qualifies the sample before the estimator sees it.
            val rejection = sample?.let { session.update(it) }
            _telemetry.value = buildTelemetry(callbackNanos)
            // [GPS-0.4] Schema-v2 drive trace: raw fix + estimator outcome + signal snapshot.
            fieldLogger?.log(
                loc,
                callbackNanos,
                mock,
                EstimatorOutcome(session.estimateAt(callbackNanos), rejection, session.lastNis),
                diagnostics.latest,
            )
        }
    }

    /** Call under [lock]. */
    private fun buildTelemetry(nowNanos: Long): TelemetryData {
        val fix = lastFix
        val estimate = session.estimateAt(nowNanos)
        return TelemetryData(
            speedKmh = (fix?.takeIf { it.hasSpeed() }?.speed ?: 0f) * 3.6f,
            // Numeric value is diagnostic when INVALID [GPS-D4] — consumers
            // gate on speedValidity [GPS-09], the UI shows "--".
            theoreticalSpeedKmh = estimate.speedMps * 3.6f,
            speedValidity = estimate.validity,
            // [GPS-4.1] σ rides along in km/h for the order error budget.
            theoreticalSpeedSigmaKmh = estimate.speedSigmaMps?.times(KMH_PER_MPS),
            accelerationG = estimate.accelerationMps2 / 9.81f,
            altitude = fix?.takeIf { it.hasAltitude() }?.altitude ?: 0.0,
            latitude = fix?.latitude ?: 0.0,
            longitude = fix?.longitude ?: 0.0,
            gpsStatus = qualityOf(fix, nowNanos, lastFixElapsedNanos),
            // [S2] Schema-v2 export fields.
            speedAccuracyMs =
                if (Build.VERSION.SDK_INT >= 26 && fix?.hasSpeedAccuracy() == true) {
                    fix.speedAccuracyMetersPerSecond
                } else {
                    0f
                },
            elapsedRealtimeNanos = fix?.elapsedRealtimeNanos ?: 0L,
        )
    }

    private companion object {
        const val TAG = "SpeedProvider"
        const val FUSED_FALLBACK_INTERVAL_MS = 500L
        const val KMH_PER_MPS = 3.6f
    }
}

private const val STALE_FIX_NANOS = 5_000_000_000L
private const val SPEED_ACC_GOOD_MPS = 0.5f
private const val SPEED_ACC_POOR_MPS = 1.5f
private const val HORIZ_ACC_GOOD_M = 10f
private const val HORIZ_ACC_POOR_M = 30f
private const val HORIZ_ACC_UNKNOWN_M = 999f

/** [GPS-12] Mock-provider flag, recorded in samples and traces. */
private fun isMockFix(loc: Location): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        loc.isMock
    } else {
        @Suppress("DEPRECATION")
        loc.isFromMockProvider
    }

/** [G3] LED keyed to speed accuracy — the error term the app lives on. */
private fun qualityOf(
    fix: Location?,
    nowNanos: Long,
    lastFixElapsedNanos: Long,
): GpsStatus {
    if (fix == null || nowNanos - lastFixElapsedNanos > STALE_FIX_NANOS) return GpsStatus.NONE
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fix.hasSpeedAccuracy()) {
        when {
            fix.speedAccuracyMetersPerSecond <= SPEED_ACC_GOOD_MPS -> GpsStatus.GOOD
            fix.speedAccuracyMetersPerSecond <= SPEED_ACC_POOR_MPS -> GpsStatus.POOR
            else -> GpsStatus.NONE
        }
    } else {
        // Pre-API-26 fallback proxy — display only, never a σv substitute [GPS-1.3].
        val h = if (fix.hasAccuracy()) fix.accuracy else HORIZ_ACC_UNKNOWN_M
        when {
            h <= HORIZ_ACC_GOOD_M -> GpsStatus.GOOD
            h <= HORIZ_ACC_POOR_M -> GpsStatus.POOR
            else -> GpsStatus.NONE
        }
    }
}
