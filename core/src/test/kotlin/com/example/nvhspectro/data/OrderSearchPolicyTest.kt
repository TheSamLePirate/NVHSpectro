package com.example.nvhspectro.data

import com.example.nvhspectro.OrderTrackingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [plan-gps GPS-4.1/4.2] The kinematic error budget and the dynamic search
 * window that closes GPS-10 (fixed ±1 bin vs σf ≈ 54 Hz).
 */
class OrderSearchPolicyTest {
    // The audit's own worked example: V1000 = 10 km/h, H18, σv = 0.5 m/s.
    private val auditSigmaKmh = 0.5 * 3.6
    private val auditV1000 = 10.0
    private val df44k2048 = 44100.0 / 2048.0 // ≈ 21.53 Hz

    @Test
    fun gps4_errorBudget_matchesTheAuditArithmetic() {
        // σrpm = 1.8 · 1000 / 10 = 180 rpm; σf(H18) = 18 · 180 / 60 = 54 Hz.
        assertEquals(180.0, OrderSearchPolicy.sigmaRpm(auditSigmaKmh, auditV1000), 1e-9)
        assertEquals(54.0, OrderSearchPolicy.sigmaOrderFreqHz(18.0, auditSigmaKmh, auditV1000), 1e-9)
    }

    @Test
    fun gps10_auditCase_isHonestlySuspendedInsteadOfMisread() {
        // At 30 km/h: h1 = 50 Hz. The 95 % window (2·54 + Δf ≈ 129.5 Hz) spans
        // several orders — the old ±1-bin code reported SOMETHING there; the
        // policy now suspends [GPS-4.2].
        val w =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = OrderSearchPolicy.sigmaOrderFreqHz(18.0, auditSigmaKmh, auditV1000),
                h1FreqHz = 50.0,
                dfHz = df44k2048,
                legacyRadiusBins = 1,
            )
        assertEquals(2.0 * 54.0 + df44k2048, w.halfWidthHz, 1e-6)
        assertFalse("k·σf = 108 Hz vs a 25 Hz bound is ambiguous", w.identifiable)
    }

    @Test
    fun gps10_preciseFix_getsAWindowThatContainsTheTrueLine() {
        // Doppler-grade σv = 0.1 m/s at a realistic gear (V1000 = 40 km/h),
        // H18 at 60 km/h: rpm = 1500, h1 = 25 Hz, target = 450 Hz.
        val sigmaKmh = 0.1 * 3.6
        val w =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = OrderSearchPolicy.sigmaOrderFreqHz(18.0, sigmaKmh, 40.0),
                h1FreqHz = 25.0,
                dfHz = df44k2048,
                legacyRadiusBins = 1,
            )
        // σf = 18 · (0.36·1000/40) / 60 = 2.7 Hz → half-width 2·2.7 + 21.5 ≈ 26.9 Hz.
        assertTrue(w.identifiable)
        assertTrue("window must cover k·σf (${w.halfWidthHz})", w.halfWidthHz >= 2.0 * 2.7)
        assertEquals(2, w.radiusBins)

        // Gate GPS-4: a true line 1σ off the projection sits INSIDE the new
        // window and OUTSIDE the old ±1-bin one when σf exceeds Δf… here it
        // is found by the widened search where legacy already covered ±1 bin.
        val bins = 1024
        val abs = FloatArray(bins) { -90f }
        val ttnr = FloatArray(bins)
        val projectedHz = 450.0
        val trueHz = projectedHz + 2.7 // 1σ high
        abs[Math.round(trueHz / df44k2048).toInt()] = -20f
        val levels = OrderTrackingEngine.searchTrackedOrder(abs, ttnr, projectedHz, df44k2048, w.radiusBins)
        assertTrue("true line must be captured (got ${levels.dbFS})", levels.dbFS > -30.0)
    }

    @Test
    fun gps10_sigmaBeyondOneBin_widensTheSearchThatUsedToMissTheLine() {
        // σf = 3 bins: the true line at +2σ is 6 bins off the projection —
        // invisible to the historical ±1-bin search, captured by the policy's.
        val df = 10.0
        val sigmaF = 30.0 // Hz
        // Solve backward: order = 2, choose σv/V1000 giving σf = 30.
        // σf = n·σv·1000/(V1000·60) → with n = 2, V1000 = 10: σv = 9 km/h.
        val w =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = OrderSearchPolicy.sigmaOrderFreqHz(2.0, 9.0, 10.0),
                h1FreqHz = 200.0,
                dfHz = df,
                legacyRadiusBins = 1,
            )
        assertEquals(sigmaF, w.sigmaFreqHz!!, 1e-9)
        assertTrue(w.identifiable) // 70 Hz half-width ≤ 100 Hz bound
        val bins = 512
        val abs = FloatArray(bins) { -90f }
        val projectedHz = 400.0
        val trueBin = Math.round((projectedHz + 2 * sigmaF) / df).toInt()
        abs[trueBin] = -15f
        val old = OrderTrackingEngine.searchTrackedOrder(abs, FloatArray(bins), projectedHz, df, 1)
        val new = OrderTrackingEngine.searchTrackedOrder(abs, FloatArray(bins), projectedHz, df, w.radiusBins)
        assertTrue("old fixed window misses the line", old.dbFS < -60.0)
        assertTrue("dynamic window captures it", new.dbFS > -25.0)
    }

    @Test
    fun gps4_unknownSigma_fallsBackToTheLegacyFixedRadius() {
        val w =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = null,
                h1FreqHz = 50.0,
                dfHz = df44k2048,
                legacyRadiusBins = 3,
            )
        assertTrue(w.identifiable)
        assertEquals(3, w.radiusBins)
        assertNull(w.sigmaFreqHz)
    }

    @Test
    fun gps4_windowNeverCollapsesBelowOneBin() {
        val w =
            OrderSearchPolicy.windowFor(
                sigmaFreqHz = OrderSearchPolicy.sigmaOrderFreqHz(1.0, 0.01, 160.0),
                h1FreqHz = 500.0,
                dfHz = df44k2048,
                legacyRadiusBins = 1,
            )
        assertTrue(w.identifiable)
        assertTrue(w.radiusBins >= 1)
    }
}
