package com.example.nvhspectro.data

import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [D4, plan 2.5] Analytical magnitude-response checks of the biquad cascades. */
class BiQuadFilterTest {

    private val butterworth8Q = listOf(0.509795579, 0.601344887, 0.899976223, 2.562915448)
    private val sr = 44100.0

    private fun cascadeDb(sections: List<BiQuadFilter>, freqHz: Double): Double =
        20.0 * log10(sections.fold(1.0) { acc, s -> acc * s.magnitudeAt(freqHz) })

    @Test
    fun d4_eighthOrderLowPass_isMinus3dbAtCutoff() {
        val fc = 1000.0
        val lp = butterworth8Q.map { q -> BiQuadFilter(FilterType.LOW_PASS, 0.0, fc, sr, q) }
        assertEquals(-3.01, cascadeDb(lp, fc), 0.15)
        assertEquals(0.0, cascadeDb(lp, 100.0), 0.1) // passband flat
        // 8th order ≈ -48 dB/octave: one octave above the cutoff
        assertTrue(cascadeDb(lp, 2000.0) < -45.0)
    }

    @Test
    fun d4_eighthOrderHighPass_isMinus3dbAtCutoff() {
        val fc = 1000.0
        val hp = butterworth8Q.map { q -> BiQuadFilter(FilterType.HIGH_PASS, fc, 0.0, sr, q) }
        assertEquals(-3.01, cascadeDb(hp, fc), 0.15)
        assertEquals(0.0, cascadeDb(hp, 8000.0), 0.1)
        assertTrue(cascadeDb(hp, 500.0) < -45.0)
    }

    /** The composite band-pass (HP min + LP max cascades) that C10/D4 now build. */
    @Test
    fun d4_bandPassComposite_edgesAtMinus3_centerFlat() {
        val fMin = 500.0
        val fMax = 4000.0
        val band = butterworth8Q.map { q -> BiQuadFilter(FilterType.HIGH_PASS, fMin, fMax, sr, q) } +
            butterworth8Q.map { q -> BiQuadFilter(FilterType.LOW_PASS, fMin, fMax, sr, q) }
        assertEquals(0.0, cascadeDb(band, 1500.0), 0.2) // wide band: flat center
        assertEquals(-3.01, cascadeDb(band, fMin), 0.3)
        assertEquals(-3.01, cascadeDb(band, fMax), 0.3)
        assertTrue(cascadeDb(band, 100.0) < -40.0)
        assertTrue(cascadeDb(band, 12000.0) < -40.0)
    }

    @Test
    fun bandStopNotchCascade_rejectsCenterAndPassesOutside() {
        val notch = butterworth8Q.map { q -> BiQuadFilter(FilterType.BAND_STOP, 800.0, 1200.0, sr, q) }
        assertTrue("center must be strongly rejected", cascadeDb(notch, 1000.0) < -40.0)
        assertEquals(0.0, cascadeDb(notch, 100.0), 0.5)
        assertEquals(0.0, cascadeDb(notch, 8000.0), 0.5)
    }

    @Test
    fun processSample_impulseResponse_matchesAnalyticalDcGain() {
        // DC gain of a low-pass section ≈ 1: sum of the impulse response converges to it.
        val lp = BiQuadFilter(FilterType.LOW_PASS, 0.0, 1000.0, sr, 0.707)
        var sum = 0.0
        for (i in 0 until 20000) {
            sum += lp.processSample(if (i == 0) 1.0 else 0.0)
        }
        assertEquals(1.0, sum, 1e-3)
    }
}
