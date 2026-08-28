package com.example.nvhspectro

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt
import org.jtransforms.fft.DoubleFFT_1D

/**
 * FFT + tonal-emergence processing for ONE audio stream.
 *
 * Stateful (EMA integration, shock detector): create one instance per stream
 * and never share it between live capture and file analysis [audit D3].
 * The sample rate is fixed per instance and threaded from the actual source
 * [audit C1] — never assume a rate here.
 *
 * [plan 3.7] DSP core polish:
 * - real FFT ([DoubleFFT_1D.realForward]) on preallocated, reused buffers —
 *   half the work and zero per-frame allocation of the old complexForward
 *   with zeroed imaginary parts [D6]. The returned magnitude array is REUSED
 *   across calls: copy it if you retain it.
 * - No sentinel arithmetic: the TTNR scale is emergence dB with 0 = none;
 *   temporal integration runs on LINEAR power with the honest time constant
 *   [TTNR_INTEGRATION_TAU_SEC] (α is derived from the real frame interval,
 *   so integration time no longer changes with FFT size) [D2].
 * - The shock detector compares energy RISE RATE (dB/s), not per-call deltas,
 *   and the first frame of a stream is ANALYZED (the historical −120
 *   initialization squelched it unconditionally) [D3].
 * - Sub-30 Hz masking is display policy and lives in the display layer
 *   ([AudioConfig.DISPLAY_MIN_FREQ_HZ]) — magnitudes here are true [D7].
 */
class FFTProcessor(val fftSize: Int = AudioConfig.DEFAULT_FFT_SIZE, private val sampleRateHz: Int) {
    private val fft = DoubleFFT_1D(fftSize.toLong())

    /** NaN = no previous frame: the first frame has no shock reference [D3]. */
    private var lastFrameEnergyDb: Double = Double.NaN
    private var integratedTtnrPower: DoubleArray? = null

    /** Bin width in Hz for this instance's stream. */
    private val df = sampleRateHz.toDouble() / fftSize

    /** Frame interval at the pipeline's 50 % overlap. */
    private val frameDtSec = (fftSize / 2.0) / sampleRateHz

    /** [D2] α from the honest τ: at 2048/44.1 kHz this is the historical 0.36. */
    private val integrationAlpha = 1.0 - exp(-frameDtSec / TTNR_INTEGRATION_TAU_SEC)

    // Fenêtrage de Hanning pour réduire le "leakage"
    private val window = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1)))
    }

    // [D6] Preallocated work buffers, reused every call.
    private val fftBuffer = DoubleArray(fftSize)
    private val magnitudes = DoubleArray(fftSize / 2)

    /**
     * Calcule la FFT sur un bloc audio.
     * @param audioData bloc de taille >= fftSize
     * @return magnitudes dBFS (moitié du tableau, signal réel). Le tableau est
     *   RÉUTILISÉ à chaque appel — copier avant de le conserver.
     */
    fun processFFT(audioData: ShortArray): DoubleArray {
        val size = minOf(audioData.size, fftSize)
        for (i in 0 until size) {
            // Normalisation 16-bit [-1.0, 1.0] et fenêtre de Hanning.
            fftBuffer[i] = (audioData[i].toDouble() / 32768.0) * window[i]
        }
        for (i in size until fftSize) {
            fftBuffer[i] = 0.0
        }

        // [D6] Real-input FFT: half the work of complexForward on zeroed ims.
        fft.realForward(fftBuffer)

        val normFactor = fftSize / 4.0
        for (i in 0 until fftSize / 2) {
            // realForward packing (n even): a[0]=Re[0]; a[2k]=Re[k], a[2k+1]=Im[k].
            val re = if (i == 0) fftBuffer[0] else fftBuffer[2 * i]
            val im = if (i == 0) 0.0 else fftBuffer[2 * i + 1]
            val mag = sqrt(re * re + im * im)
            val magNormalized = mag / normFactor
            magnitudes[i] = if (magNormalized > 1e-6) 20 * log10(magNormalized) else -120.0
        }
        return magnitudes
    }

    /**
     * Spectre d'émergence tonale (heuristique NVH hybride — voir audit D1 :
     * PAS une implémentation ECMA-74/ISO 1996-2 conforme).
     *
     * @param magnitudesDbFS magnitudes en dBFS
     * @return émergence en dB, échelle [0, 30] — 0 = aucune émergence [D2].
     */
    fun computeTTNR(magnitudesDbFS: DoubleArray): DoubleArray {
        val binCount = magnitudesDbFS.size
        val rawTtnr = DoubleArray(binCount)

        // Convertir dBFS en puissance linéaire P = 10^(dBFS / 10)
        var totalFrameEnergySum = 0.0
        val powerLinear = DoubleArray(binCount) { i ->
            val p = Math.pow(10.0, magnitudesDbFS[i] / 10.0)
            totalFrameEnergySum += p
            p
        }

        // 1. DÉTECTEUR DE CHOC TEMPOREL [D3] : taux de montée en dB/s (indépendant
        // de la taille FFT), sans référence factice pour la première trame.
        val currentFrameEnergyDb = 10.0 * log10(totalFrameEnergySum.coerceAtLeast(1e-12))
        val previousEnergyDb = lastFrameEnergyDb
        lastFrameEnergyDb = currentFrameEnergyDb
        val isTransientShock = !previousEnergyDb.isNaN() &&
            (currentFrameEnergyDb - previousEnergyDb) > SHOCK_RISE_DB_PER_SECOND * frameDtSec

        if (!isTransientShock) {
            for (i in 0 until binCount) {
                val f = i * df

                // Porte d'amplitude profilée selon la fréquence (Double Verrou HF pour MLI)
                val minMagnitudeGate = when {
                    f < 500.0 -> -75.0
                    f < 4000.0 -> -85.0
                    else -> -75.0 // -75 dBFS en HF: filtre 99.9% de la MLI benigne, capture 100% de la MLI défectueuse
                }

                // Filtre Passe-Haut NVH 43 Hz (Filtre Colormaps) + Porte d'amplitude profilée
                if (f < 43.0 || magnitudesDbFS[i] < minMagnitudeGate) {
                    continue
                }

                // Condition de Pic Local Strict : Seuls les vrais sommets de pics sont évalués
                val isStrictLocalPeak = i > 0 && i < binCount - 1 &&
                    magnitudesDbFS[i] > magnitudesDbFS[i - 1] &&
                    magnitudesDbFS[i] > magnitudesDbFS[i + 1]

                if (!isStrictLocalPeak) {
                    continue
                }

                // Largeur de bande critique (Formule de Terhardt) & Masquage Local Adaptatif NVH (max 350 Hz)
                val fKhz = f / 1000.0
                val criticalBandwidth = 25.0 + 75.0 * Math.pow(1.0 + 1.4 * fKhz * fKhz, 0.69)
                val localMaskingBandwidth = minOf(criticalBandwidth, 350.0)
                val halfCbBins = (localMaskingBandwidth / (2.0 * df)).toInt().coerceAtLeast(4)

                // Règle générique : La bande de bruit minimale doit être strictement >= 45 Hz (Filtre colormaps 43 Hz + 2 Hz de marge)
                val minNoiseFreqHz = (i - halfCbBins) * df
                if (minNoiseFreqHz < 45.0) {
                    continue
                }

                val minBin = (i - halfCbBins).coerceAtLeast(0)
                val maxBin = (i + halfCbBins).coerceAtMost(binCount - 1)

                // Puissance brute du ton (Somme du pic i et de ses 4 raies adjacentes de leakage +-2 bins)
                var pToneGross = powerLinear[i]
                if (i > 0) pToneGross += powerLinear[i - 1]
                if (i > 1) pToneGross += powerLinear[i - 2]
                if (i < binCount - 1) pToneGross += powerLinear[i + 1]
                if (i < binCount - 2) pToneGross += powerLinear[i + 2]

                // Puissance du bruit ambiant local
                var pNoiseSum = 0.0
                var noiseCount = 0

                for (j in minBin..maxBin) {
                    if (Math.abs(j - i) > 3) {
                        pNoiseSum += powerLinear[j]
                        noiseCount++
                    }
                }

                if (noiseCount == 0 || pNoiseSum <= 0.0) {
                    continue
                }

                val pNoiseDensityPerHz = pNoiseSum / (noiseCount * df)
                val pNoiseIn5Bins = 5.0 * pNoiseDensityPerHz * df

                // Puissance NETTE du ton (Soustraction du bruit de fond sous le dôme)
                val pToneNet = maxOf(0.0, pToneGross - pNoiseIn5Bins)

                // Largeur de bande critique stabilisée (borne min 150 Hz pour éviter l'explosion du ratio en BF)
                val cbwEffective = maxOf(criticalBandwidth, 150.0)
                val pNoiseTotalInCb = pNoiseDensityPerHz * cbwEffective

                // Ratio tonal sur puissance nette du ton
                val ratioCb = if (pNoiseTotalInCb > 0.0) pToneNet / pNoiseTotalInCb else 0.0
                val ttnrCbDb = if (ratioCb > 1.0) 10.0 * log10(ratioCb) else 0.0

                // Émergence spectrale locale
                val localNoiseFloorDbFS = 10.0 * log10(pNoiseDensityPerHz * df)
                val localEmergenceDb = (magnitudesDbFS[i] - localNoiseFloorDbFS).coerceAtLeast(0.0)

                // Seuil d'émergence minimale (anti-turbulences & double verrou HF)
                val minEmergenceRequired = -3.0

                // [D2] Hybridation NVH : échelle honnête [0, 30], 0 = pas d'émergence
                // (l'ancienne fenêtre −3..0 était invisible pour tous les consommateurs).
                val finalPeakTtnr = if (pToneNet > 0.0 && localEmergenceDb >= minEmergenceRequired) {
                    maxOf(ttnrCbDb, localEmergenceDb - 1.5).coerceIn(0.0, 30.0)
                } else {
                    0.0
                }

                if (finalPeakTtnr > 0.0) {
                    rawTtnr[i] = finalPeakTtnr
                    // Reconstitution de la largeur physique du dôme (Leakage Hanning sur bins adjacents)
                    val domeLevel = (finalPeakTtnr - 4.0).coerceAtLeast(0.0)
                    if (i > 0 && rawTtnr[i - 1] < domeLevel) {
                        rawTtnr[i - 1] = domeLevel
                    }
                    if (i < binCount - 1 && rawTtnr[i + 1] < domeLevel) {
                        rawTtnr[i + 1] = domeLevel
                    }
                }
            }
        }

        // 2. Filtre de Prominence Spectrale (Anti-Spike 1-Pixel)
        val filteredTtnr = DoubleArray(binCount)
        for (i in 0 until binCount) {
            val valCurr = rawTtnr[i]
            if (valCurr <= 0.0) continue

            val prevVal = if (i > 0) rawTtnr[i - 1] else 0.0
            val nextVal = if (i < binCount - 1) rawTtnr[i + 1] else 0.0

            val hasStructure = (prevVal >= valCurr - 8.0 || nextVal >= valCurr - 8.0)
            if (hasStructure) {
                filteredTtnr[i] = valCurr
            }
        }

        // 3. INTÉGRATION TEMPORELLE [D2] : EMA sur puissance LINÉAIRE (une
        // disparition de ton décroît exponentiellement au lieu d'un blend de
        // sentinelles), τ honnête = TTNR_INTEGRATION_TAU_SEC.
        val finalTtnr = DoubleArray(binCount)
        val prevPower = integratedTtnrPower
            ?.takeIf { it.size == binCount }
            ?: DoubleArray(binCount) { 1.0 } // 1.0 = 0 dB = aucune émergence
        for (i in 0 until binCount) {
            val pRaw = Math.pow(10.0, filteredTtnr[i] / 10.0)
            val pInt = (1.0 - integrationAlpha) * prevPower[i] + integrationAlpha * pRaw
            prevPower[i] = pInt
            val db = 10.0 * log10(pInt)
            // Plancher de détection (= seuil noir de l'affichage) : un zéro franc
            // sur l'échelle d'émergence, pas une sentinelle.
            finalTtnr[i] = if (db >= DETECTION_FLOOR_DB) db else 0.0
        }
        integratedTtnrPower = prevPower

        return finalTtnr
    }

    companion object {
        /**
         * [D2] Historical behavior made honest: α=0.36 at the 23.2 ms frame
         * interval of 2048/44.1 kHz ⇒ τ = −Δt/ln(1−α) ≈ 52 ms. (Comments used
         * to claim 220 ms and 110 ms; both contradicted the math.)
         */
        const val TTNR_INTEGRATION_TAU_SEC = 0.052

        /**
         * [D3] Historical sensitivity (6 dB per frame at 43 fps) expressed as
         * a rate so it no longer changes silently with FFT size.
         */
        const val SHOCK_RISE_DB_PER_SECOND = 258.0

        /** Below this integrated emergence the output is plain 0 (display black threshold). */
        const val DETECTION_FLOOR_DB = 1.0
    }
}
