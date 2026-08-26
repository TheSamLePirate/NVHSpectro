package com.example.nvhspectro

import com.example.nvhspectro.testutil.SynthSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [C6/L3/L7, plan 2.2/2.3] The engine owning all live-DSP state, and the
 * reset() contract from the audit's mode-transition table: nothing built under
 * a previous stream/config survives a transition.
 */
class LiveAnalysisEngineTest {

    private val fftSize = 2048
    private val sampleRate = 44100
    private val toneBuffer = SynthSignals.sine(
        SynthSignals.binCenteredFreq(200, fftSize, sampleRate), // ≈ 4306 Hz
        sampleRate, fftSize, amplitude = 0.003 // ≈ -50 dBFS tone
    )

    @Test
    fun steadyTone_buildsEmergenceThroughPersistenceAndEma() {
        val engine = LiveAnalysisEngine(fftSize, sampleRate)
        var last = engine.processFrame(toneBuffer)
        repeat(11) { last = engine.processFrame(toneBuffer) }
        assertTrue(
            "tone must be emergent after 12 frames, got ${last.ttnrSpectrum.max()}",
            last.ttnrSpectrum.max() > 5.0
        )
    }

    @Test
    fun retroUnmask_firesOnceWithFiveRawRows() {
        val engine = LiveAnalysisEngine(fftSize, sampleRate)
        var retroFrames = 0
        var lastRowsSize = 0
        repeat(10) {
            val r = engine.processFrame(toneBuffer)
            if (r.retroUnmaskBins.isNotEmpty()) {
                retroFrames++
                lastRowsSize = r.retroRawRows.size
            }
        }
        assertEquals("retro-unmask must fire exactly once for a steady tone", 1, retroFrames)
        assertEquals(LiveAnalysisEngine.RETRO_FRAMES - 1, lastRowsSize)
    }

    @Test
    fun l7_reset_restoresFirstFrameShockSquelch() {
        val engine = LiveAnalysisEngine(fftSize, sampleRate)
        repeat(12) { engine.processFrame(toneBuffer) }
        engine.reset()
        // A fresh stream's first frame is always squelched (D3 pinned behavior) —
        // proving the FFT shock/integration state did not survive the reset.
        val first = engine.processFrame(toneBuffer)
        assertTrue(
            "first post-reset frame must report no emergence, got ${first.ttnrSpectrum.max()}",
            first.ttnrSpectrum.max() <= 0.0
        )
    }

    @Test
    fun l7_reset_clearsOrderDomainEmaGhosts() {
        val engine = LiveAnalysisEngine(fftSize, sampleRate)
        val excited = FloatArray(LiveAnalysisEngine.ORDER_BINS).also { it[180] = 10f }
        repeat(20) { engine.blendOrderEma(excited) }
        assertTrue(engine.blendOrderEma(FloatArray(LiveAnalysisEngine.ORDER_BINS))[180] > 2f)

        engine.reset()
        val afterReset = engine.blendOrderEma(FloatArray(LiveAnalysisEngine.ORDER_BINS))
        assertEquals(
            "order EMA must not carry ghosts across a reset [L7]",
            0f, afterReset[180], 1e-6f
        )
    }
}
