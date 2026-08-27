package com.example.nvhspectro.data

import com.example.nvhspectro.testutil.SynthSignals
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [C10/D4, plan 2.5/3.6] The analysis/playback filter chain builder + PCM
 * renderer, extracted pure.
 */
class FilterChainTest {

    private val sampleRate = 44100

    private fun rms(pcm: ShortArray): Double =
        sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)

    @Test
    fun d4_bandPass_isHpLpCascade_ofEightSections() {
        val biquads = FilterChain.buildBiquads(listOf(FilterSpec(FilterType.BAND_PASS, 500, 2000)), sampleRate.toDouble())
        assertEquals("true band-pass = 4 HP + 4 LP Butterworth sections", 8, biquads.size)
    }

    @Test
    fun singleBandFilters_useFourSections() {
        assertEquals(4, FilterChain.buildBiquads(listOf(FilterSpec(FilterType.LOW_PASS, 0, 1000)), sampleRate.toDouble()).size)
        assertEquals(4, FilterChain.buildBiquads(listOf(FilterSpec(FilterType.BAND_STOP, 500, 2000)), sampleRate.toDouble()).size)
    }

    @Test
    fun c10_lowPass_attenuatesAToneWellAboveCutoff() {
        val tone = SynthSignals.sine(8000.0, sampleRate, sampleRate / 2, amplitude = 0.5)
        val filtered = FilterChain.renderFilteredPcm(
            tone, listOf(FilterSpec(FilterType.LOW_PASS, 0, 1000)), sampleRate.toDouble()
        )
        val attenuationDb = 20 * Math.log10(rms(filtered) / rms(tone))
        // The analytic 8th-order slope gives far more, but the rendered PCM is
        // 16-bit: quantization floors the measurable attenuation around -56 dB.
        assertTrue("8 kHz through an 8th-order 1 kHz LP must drop >50 dB, got $attenuationDb", attenuationDb < -50.0)
    }

    @Test
    fun c10_lowPass_passesAToneWellBelowCutoff() {
        val tone = SynthSignals.sine(200.0, sampleRate, sampleRate / 2, amplitude = 0.5)
        val filtered = FilterChain.renderFilteredPcm(
            tone, listOf(FilterSpec(FilterType.LOW_PASS, 0, 4000)), sampleRate.toDouble()
        )
        val gainDb = 20 * Math.log10(rms(filtered) / rms(tone))
        assertTrue("in-band tone must pass (~0 dB), got $gainDb", gainDb > -1.0)
    }

    @Test
    fun l2_checkActive_abortsMidRender() {
        val pcm = ShortArray(1 shl 18)
        var calls = 0
        try {
            FilterChain.renderFilteredPcm(pcm, listOf(FilterSpec(FilterType.LOW_PASS, 0, 1000)), sampleRate.toDouble()) {
                if (++calls >= 2) throw InterruptedException("cancelled")
            }
            throw AssertionError("render must stop when checkActive throws")
        } catch (e: InterruptedException) {
            assertEquals(2, calls)
        }
    }
}
