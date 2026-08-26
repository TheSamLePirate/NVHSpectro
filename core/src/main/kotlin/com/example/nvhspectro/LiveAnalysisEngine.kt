package com.example.nvhspectro

/**
 * [C6, L3, plan 2.2] ALL mutable live-DSP state lives here — the ViewModel
 * keeps no analysis fields. Methods are synchronized as a belt on top of the
 * single-DSP-thread confinement (reset() may be invoked from the main thread
 * on mode/config transitions [L7]).
 *
 * Behavior is a faithful extraction of the historical live path (persistence
 * counters, 150 ms retro-unmask buffer, 0.75/0.25 EMA). Order-domain work
 * (EMA blend, tag detection) lives in [OrderTrackingEngine] [plan 3.2].
 */
class LiveAnalysisEngine(val fftSize: Int, private val sampleRateHz: Int) {

    private var fftProcessor = FFTProcessor(fftSize, sampleRateHz)
    private var previousTTNRSpectrum = DoubleArray(0)
    private var ttnrPersistenceCount = IntArray(0)
    private val recentRawTTNR = ArrayDeque<DoubleArray>() // newest first

    class FrameResult(
        /** [P1, plan 3.5] Spectra cross the engine boundary as FloatArray — display precision at half the memory. */
        val magnitudes: FloatArray,
        val ttnrSpectrum: FloatArray,
        /** Bins whose 6-frame persistence just completed — history rows 1..5 get retro-unmasked. */
        val retroUnmaskBins: List<Int>,
        /** Raw TTNR rows k=1..5 (newest first) when retro fires, else empty. */
        val retroRawRows: List<FloatArray>
    )

    @Synchronized
    fun processFrame(buffer: ShortArray): FrameResult {
        val magnitudes = fftProcessor.processFFT(buffer)
        val rawTtnr = fftProcessor.computeTTNR(magnitudes)

        if (ttnrPersistenceCount.size != rawTtnr.size) {
            ttnrPersistenceCount = IntArray(rawTtnr.size)
            recentRawTTNR.clear()
        }
        recentRawTTNR.addFirst(rawTtnr.clone())
        while (recentRawTTNR.size > RETRO_FRAMES) {
            recentRawTTNR.removeLast()
        }

        val validated = DoubleArray(rawTtnr.size)
        val retroBins = mutableListOf<Int>()
        for (i in rawTtnr.indices) {
            if (rawTtnr[i] >= -3.0) {
                ttnrPersistenceCount[i]++
            } else {
                ttnrPersistenceCount[i] = 0
            }
            if (ttnrPersistenceCount[i] >= PERSISTENCE_FRAMES) {
                validated[i] = rawTtnr[i]
                if (ttnrPersistenceCount[i] == PERSISTENCE_FRAMES) {
                    retroBins.add(i)
                }
            } else {
                validated[i] = -100.0
            }
        }

        val smoothed = DoubleArray(rawTtnr.size)
        if (previousTTNRSpectrum.size == rawTtnr.size) {
            for (i in rawTtnr.indices) {
                smoothed[i] = 0.75 * validated[i] + 0.25 * previousTTNRSpectrum[i]
            }
        } else {
            System.arraycopy(validated, 0, smoothed, 0, validated.size)
        }
        previousTTNRSpectrum = smoothed

        val retroRows = if (retroBins.isNotEmpty() && recentRawTTNR.size >= RETRO_FRAMES) {
            recentRawTTNR.drop(1).take(RETRO_FRAMES - 1).map { it.toFloatSpectrum() }
        } else {
            emptyList()
        }
        return FrameResult(magnitudes.toFloatSpectrum(), smoothed.toFloatSpectrum(), retroBins, retroRows)
    }

    /**
     * [L7] Full state wipe on any source/config transition: shock detector,
     * TTNR integration, persistence and retro buffer — ghost data from a
     * previous session/config can no longer re-fire. (The companion order
     * EMA is reset on its own [OrderTrackingEngine.reset].)
     */
    @Synchronized
    fun reset() {
        fftProcessor = FFTProcessor(fftSize, sampleRateHz)
        previousTTNRSpectrum = DoubleArray(0)
        ttnrPersistenceCount = IntArray(0)
        recentRawTTNR.clear()
    }

    companion object {
        const val PERSISTENCE_FRAMES = 6
        const val RETRO_FRAMES = 6
    }
}

/** [P1, plan 3.5] Spectrum storage/display conversion — computation stays double inside the DSP. */
fun DoubleArray.toFloatSpectrum(): FloatArray = FloatArray(size) { this[it].toFloat() }
