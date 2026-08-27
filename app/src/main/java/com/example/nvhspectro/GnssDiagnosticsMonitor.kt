package com.example.nvhspectro

import android.annotation.SuppressLint
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.util.Log
import com.example.nvhspectro.data.GnssDiagnostics
import java.util.concurrent.Executor

/**
 * [plan-gps GPS-3.3/3.4/3.5] GNSS observability around the speed chain:
 *
 * - snapshots `GnssStatus` (satellites, C/N0, constellations, L5) into
 *   [latest] for the drive traces — diagnostics only, never a variance
 *   substitute [GPS-12];
 * - requests full-tracking measurements (API 31+) ONLY while registered and
 *   only when [fullTrackingRequested] — the A/B switch of the GPS-5 campaign;
 *   the callback is consumed minimally (event count proves the cadence);
 * - builds the per-device capability matrix stamped into trace headers.
 *
 * Owned by [SpeedProvider]; registered/unregistered with the LIVE session.
 */
class GnssDiagnosticsMonitor(
    private val locationManager: LocationManager,
    private val handler: Handler,
    private val executor: Executor,
    private val fullTrackingRequested: Boolean,
) {
    /** [GPS-3.3] Latest signal snapshot; null while unregistered. */
    @Volatile
    var latest: GnssDiagnostics? = null
        private set

    @Volatile
    private var measurementEventCount = 0L

    private val statusCallback =
        object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                latest = snapshot(status)
            }
        }

    private val measurementsCallback: GnssMeasurementsEvent.Callback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    measurementEventCount++
                }
            }
        } else {
            null
        }

    @SuppressLint("MissingPermission")
    fun register() {
        // [GPS-3.3] Signal observability; failures only cost diagnostics.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(executor, statusCallback)
            } else {
                locationManager.registerGnssStatusCallback(statusCallback, handler)
            }
        }.onFailure { Log.w(TAG, "GnssStatus callback unavailable", it) }
        // [GPS-3.4] Full tracking only during an active measurement session.
        val callback = measurementsCallback
        if (fullTrackingRequested && callback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val request = GnssMeasurementRequest.Builder().setFullTracking(true).build()
                locationManager.registerGnssMeasurementsCallback(request, executor, callback)
            }.onFailure { Log.w(TAG, "full-tracking registration failed", it) }
        }
    }

    fun unregister() {
        runCatching { locationManager.unregisterGnssStatusCallback(statusCallback) }
        val callback = measurementsCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback != null) {
            if (measurementEventCount > 0) {
                Log.i(TAG, "full-tracking session delivered $measurementEventCount measurement events")
            }
            runCatching { locationManager.unregisterGnssMeasurementsCallback(callback) }
        }
        latest = null
    }

    /** [GPS-3.5] Capability matrix, stamped into every trace header. */
    fun capabilitiesLine(): String =
        buildString {
            append("sdk=${Build.VERSION.SDK_INT}")
            append(" fullTracking=$fullTrackingRequested")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                append(" gnssYear=${locationManager.gnssYearOfHardware}")
                append(" gnssHw=${locationManager.gnssHardwareModelName ?: "?"}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                append(" rawMeasurements=${locationManager.gnssCapabilities.hasMeasurements()}")
            }
        }

    /** [GPS-3.3] One [GnssDiagnostics] per satellite-status change. */
    private fun snapshot(status: GnssStatus): GnssDiagnostics {
        var used = 0
        var cn0Sum = 0f
        val constellations = sortedSetOf<String>()
        var dualFreq = false
        for (i in 0 until status.satelliteCount) {
            if (!status.usedInFix(i)) continue
            used++
            cn0Sum += status.getCn0DbHz(i)
            constellations.add(constellationName(status.getConstellationType(i)))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                status.hasCarrierFrequencyHz(i) &&
                status.getCarrierFrequencyHz(i) < L5_BAND_UPPER_HZ
            ) {
                dualFreq = true
            }
        }
        return GnssDiagnostics(
            satellitesVisible = status.satelliteCount,
            satellitesUsedInFix = used,
            meanUsedCn0DbHz = if (used > 0) cn0Sum / used else null,
            constellations = constellations.joinToString("+"),
            dualFrequencySeen = dualFreq,
        )
    }

    private fun constellationName(type: Int): String =
        when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
            GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            else -> "OTHER"
        }

    private companion object {
        const val TAG = "GnssDiagnostics"

        /** Carriers below this are L5/E5-band (≈1176–1207 MHz); L1/E1 sits at ≈1575 MHz. */
        const val L5_BAND_UPPER_HZ = 1.3e9f
    }
}
