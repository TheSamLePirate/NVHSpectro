package com.example.nvhspectro.data

/** A filter request without UI baggage — what the DSP chain needs [plan 3.3]. */
data class FilterSpec(val type: FilterType, val minFreq: Int, val maxFreq: Int)

/**
 * [C10/D4, plan 2.5/3.3] Builds and runs the playback/analysis biquad chain.
 * One rendered PCM feeds BOTH what the user hears and what the display
 * analyzes.
 */
object FilterChain {

    /** Q ladder of an 8th-order Butterworth (valid for the LP/HP sections). */
    private val BUTTERWORTH_8TH_ORDER_Q = listOf(0.509795579, 0.601344887, 0.899976223, 2.562915448)

    fun buildBiquads(filters: List<FilterSpec>, sampleRateHz: Double): List<BiQuadFilter> =
        filters.flatMap { filter ->
            when (filter.type) {
                // [D4] A true 8th-order Butterworth band-pass = HP(minFreq)
                // cascade × LP(maxFreq) cascade. (The historical 4 identical
                // band-pass sections were not Butterworth.)
                FilterType.BAND_PASS ->
                    BUTTERWORTH_8TH_ORDER_Q.map { q ->
                        BiQuadFilter(FilterType.HIGH_PASS, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sampleRateHz, q)
                    } + BUTTERWORTH_8TH_ORDER_Q.map { q ->
                        BiQuadFilter(FilterType.LOW_PASS, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sampleRateHz, q)
                    }
                // [D4] Band-stop stays a cascade of 4 identical notches (q is
                // unused by that formula): deepens/widens the rejection —
                // assumed and documented honestly.
                else ->
                    BUTTERWORTH_8TH_ORDER_Q.map { q ->
                        BiQuadFilter(filter.type, filter.minFreq.toDouble(), filter.maxFreq.toDouble(), sampleRateHz, q)
                    }
            }
        }

    /**
     * Run the chain over a whole PCM buffer. [checkActive] is invoked every
     * 64k samples so a cancelled render stops mid-file [L2].
     */
    fun renderFilteredPcm(
        pcm: ShortArray,
        filters: List<FilterSpec>,
        sampleRateHz: Double,
        checkActive: () -> Unit = {}
    ): ShortArray {
        val biquads = buildBiquads(filters, sampleRateHz)
        val filteredPcm = ShortArray(pcm.size)
        for (i in pcm.indices) {
            if (i and 0xFFFF == 0) checkActive()
            var sample = pcm[i].toDouble()
            for (bq in biquads) {
                sample = bq.processSample(sample)
            }
            filteredPcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return filteredPcm
    }
}
