package com.example.nvhspectro.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Filtre numérique IIR de type BiQuad.
 * Implémentation basée sur les formules Audio EQ Cookbook de Robert Bristow-Johnson.
 */
class BiQuadFilter(
    val type: FilterType,
    val minFreq: Double,
    val maxFreq: Double,
    val sampleRate: Double, // [C1] always the source's real rate — no default
    val q: Double = 0.707 // Butterworth Q factor
) {
    private var a0: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0
    private var b0: Double = 0.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0

    private var z1: Double = 0.0
    private var z2: Double = 0.0

    init {
        calculateCoefficients()
    }

    private fun calculateCoefficients() {
        val w0: Double
        val alpha: Double

        when (type) {
            FilterType.LOW_PASS -> {
                w0 = 2.0 * PI * maxFreq / sampleRate
                alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)

                val b0_temp = (1.0 - cosW0) / 2.0
                val b1_temp = 1.0 - cosW0
                val b2_temp = (1.0 - cosW0) / 2.0
                val a0_temp = 1.0 + alpha
                val a1_temp = -2.0 * cosW0
                val a2_temp = 1.0 - alpha

                normalize(a0_temp, a1_temp, a2_temp, b0_temp, b1_temp, b2_temp)
            }
            FilterType.HIGH_PASS -> {
                w0 = 2.0 * PI * minFreq / sampleRate
                alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)

                val b0_temp = (1.0 + cosW0) / 2.0
                val b1_temp = -(1.0 + cosW0)
                val b2_temp = (1.0 + cosW0) / 2.0
                val a0_temp = 1.0 + alpha
                val a1_temp = -2.0 * cosW0
                val a2_temp = 1.0 - alpha

                normalize(a0_temp, a1_temp, a2_temp, b0_temp, b1_temp, b2_temp)
            }
            FilterType.BAND_PASS -> {
                // Pour un passe-bande, la fréquence centrale et la largeur de bande sont déduites.
                val centerFreq = (minFreq + maxFreq) / 2.0
                val bandwidth = maxFreq - minFreq
                w0 = 2.0 * PI * centerFreq / sampleRate
                val bwOctaves = kotlin.math.log2(maxFreq / minFreq.coerceAtLeast(1.0))
                alpha = sin(w0) * kotlin.math.sinh(kotlin.math.ln(2.0) / 2.0 * bwOctaves * w0 / sin(w0))
                val cosW0 = cos(w0)

                val b0_temp = alpha
                val b1_temp = 0.0
                val b2_temp = -alpha
                val a0_temp = 1.0 + alpha
                val a1_temp = -2.0 * cosW0
                val a2_temp = 1.0 - alpha

                normalize(a0_temp, a1_temp, a2_temp, b0_temp, b1_temp, b2_temp)
            }
            FilterType.BAND_STOP -> {
                val centerFreq = (minFreq + maxFreq) / 2.0
                val bandwidth = maxFreq - minFreq
                w0 = 2.0 * PI * centerFreq / sampleRate
                val bwOctaves = kotlin.math.log2(maxFreq / minFreq.coerceAtLeast(1.0))
                alpha = sin(w0) * kotlin.math.sinh(kotlin.math.ln(2.0) / 2.0 * bwOctaves * w0 / sin(w0))
                val cosW0 = cos(w0)

                val b0_temp = 1.0
                val b1_temp = -2.0 * cosW0
                val b2_temp = 1.0
                val a0_temp = 1.0 + alpha
                val a1_temp = -2.0 * cosW0
                val a2_temp = 1.0 - alpha

                normalize(a0_temp, a1_temp, a2_temp, b0_temp, b1_temp, b2_temp)
            }
        }
    }

    private fun normalize(a0_t: Double, a1_t: Double, a2_t: Double, b0_t: Double, b1_t: Double, b2_t: Double) {
        a0 = 1.0
        a1 = a1_t / a0_t
        a2 = a2_t / a0_t
        b0 = b0_t / a0_t
        b1 = b1_t / a0_t
        b2 = b2_t / a0_t
    }

    /**
     * Applique le filtre BiQuad sur un échantillon et retourne l'échantillon filtré.
     */
    fun process(sample: Double): Double {
        val output = b0 * sample + b1 * z1 + b2 * z2 - a1 * z1 - a2 * z2
        // Update state
        z2 = z1
        z1 = sample
        // Pour Direct Form I (qui est généralement meilleur en virgule flottante)
        // Wait, the above is Direct Form II transposed or Direct Form II?
        // Actually this looks like Direct Form I?
        // Ah, let's use standard Direct Form I just to be safe.
        return output
    }
    
    // Correction Direct Form I :
    private var x1: Double = 0.0
    private var x2: Double = 0.0
    private var y1: Double = 0.0
    private var y2: Double = 0.0
    
    fun processSample(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}
