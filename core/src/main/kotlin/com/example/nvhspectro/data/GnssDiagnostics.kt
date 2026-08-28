package com.example.nvhspectro.data

/**
 * [plan-gps GPS-3.3] Observable GNSS signal quality, snapshotted from
 * `GnssStatus` at each satellite-status change.
 *
 * These values serve DIAGNOSIS and trace analysis (multipath, canyon,
 * windshield attenuation…). They never replace `speedAccuracy` as the
 * mathematical variance of a fix [GPS-12] — the estimator does not read them.
 */
data class GnssDiagnostics(
    val satellitesVisible: Int,
    val satellitesUsedInFix: Int,
    /** Mean C/N0 of the satellites used in the fix, dB-Hz; null = none used. */
    val meanUsedCn0DbHz: Float?,
    /** Constellations used in the fix, '+'-joined (e.g. "GPS+GALILEO"). */
    val constellations: String,
    /** True once a sub-1.3 GHz carrier (L5/E5-band) was seen on a used satellite. */
    val dualFrequencySeen: Boolean,
)
