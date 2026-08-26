package com.example.nvhspectro.testutil

import java.util.Random
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deterministic synthetic signals for characterization tests.
 * No Android dependencies — usable from plain JVM tests.
 */
object SynthSignals {

    /** 16-bit PCM sine. amplitude 1.0 = full scale (32767). */
    fun sine(
        freqHz: Double,
        sampleRate: Int,
        n: Int,
        amplitude: Double = 1.0,
        phase: Double = 0.0
    ): ShortArray = ShortArray(n) { i ->
        (amplitude * 32767.0 * sin(2.0 * PI * freqHz * i / sampleRate + phase))
            .roundToInt().coerceIn(-32768, 32767).toShort()
    }

    /** Frequency exactly centered on FFT bin [bin] — no scalloping loss. */
    fun binCenteredFreq(bin: Int, fftSize: Int, sampleRate: Int): Double =
        bin.toDouble() * sampleRate / fftSize

    /** Seeded uniform noise; identical output on every run. */
    fun seededNoise(n: Int, seed: Long, amplitude: Double = 0.25): ShortArray {
        val rnd = Random(seed)
        return ShortArray(n) {
            ((rnd.nextDouble() * 2.0 - 1.0) * amplitude * 32767.0)
                .roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Sum of two ShortArrays with clipping, for tone+noise mixtures. */
    fun mix(a: ShortArray, b: ShortArray): ShortArray =
        ShortArray(minOf(a.size, b.size)) { i ->
            (a[i] + b[i]).coerceIn(-32768, 32767).toShort()
        }
}
