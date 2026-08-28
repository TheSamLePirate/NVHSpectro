package com.example.nvhspectro.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-permission capability rules [U1, plan 4.1].
 *
 * The audit's dead end (`permissions.values.all { it }`) was one boolean: all three grants or
 * an unusable app. These are the four rules that replace it, and each one is a behaviour the
 * Gate-4 denial matrix walks on device.
 */
class NvhPermissionsTest {
    private fun perms(
        mic: Boolean = true,
        precise: Boolean = true,
        coarse: Boolean = true,
    ) = NvhPermissions(microphone = mic, preciseLocation = precise, coarseLocation = coarse)

    @Test
    fun u1_microphoneAlone_stillAllowsLiveCapture() {
        val p = perms(mic = true, precise = false, coarse = false)
        assertTrue("live capture must not depend on location", p.liveCapture)
        assertFalse(p.anyLocation)
        assertFalse(p.metrologicalLocation)
    }

    @Test
    fun u1_locationAlone_leavesTheAppUsableAsAnAnalyzer() {
        val p = perms(mic = false, precise = true, coarse = true)
        assertFalse("no mic means no live capture", p.liveCapture)
        assertTrue(p.metrologicalLocation)
    }

    @Test
    fun gps12_coarseLocationOnly_neverCountsAsMetrological() {
        val p = perms(precise = false, coarse = true)
        assertTrue("a coarse fix is still a location", p.anyLocation)
        assertFalse(
            "a coarse fix carries no usable Doppler speed and must not drive RPM/orders",
            p.metrologicalLocation,
        )
    }

    @Test
    fun u1_everythingDenied_isNotTreatedAsAnyCapability() {
        val p = perms(mic = false, precise = false, coarse = false)
        assertFalse(p.liveCapture)
        assertFalse(p.anyLocation)
        assertFalse(p.metrologicalLocation)
    }

    @Test
    fun u1_requestedPermissions_coverEveryCapabilityTheAppDegradesOn() {
        // A capability that is never requested can never be granted — this catches the
        // manifest/request drift that would silently disable a feature for ever.
        assertTrue(NvhPermissions.REQUESTED.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(NvhPermissions.REQUESTED.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(NvhPermissions.REQUESTED.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
