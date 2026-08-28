package com.example.nvhspectro.export

import com.example.nvhspectro.AnalysisProvenance
import com.example.nvhspectro.AudioSourceMode
import com.example.nvhspectro.data.OrderSearchPolicy
import java.util.Date
import java.util.Locale

/**
 * The traceability block printed on every exported report [U7, D1, plan 4.5, DEV-43].
 *
 * A customer-facing engineering deliverable that carries measurements but not *when*, *by
 * which build*, *from which source* and *with which speed reconstruction* they were produced
 * cannot be defended once it leaves the room. The audit found the PDF carried none of it.
 *
 * Everything here is a fact the app already knows; nothing is inferred.
 */
data class ReportStamp(
    val appVersion: String,
    val generatedAt: Date,
    val sourceLine: String,
    val analysisLine: String,
    val speedLine: String,
) {
    fun formattedTimestamp(): String = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(generatedAt)

    companion object {
        /** How the emergence metric must be named on any surface a human reads [D1, D5]. */
        const val EMERGENCE_METRIC_NAME = "Indice d'émergence NVH"

        /**
         * The honest definition, printed as a footnote: the index is an in-house
         * tonal-emergence score, NOT ECMA-74's tone-to-noise ratio, and saying so on the
         * deliverable is the difference between a measurement and a claim.
         */
        const val EMERGENCE_METRIC_NOTE =
            "$EMERGENCE_METRIC_NAME : indice d'émergence tonale propriétaire " +
                "(méthode interne, non conforme ECMA-74 / ISO 1996-2)."

        fun build(
            appVersion: String,
            generatedAt: Date,
            sourceMode: AudioSourceMode,
            provenance: AnalysisProvenance,
            sampleRateHz: Int,
            fftSize: Int,
        ): ReportStamp {
            val sourceLine =
                when (sourceMode) {
                    AudioSourceMode.LIVE ->
                        "Source : mesure en direct (micro ${provenance.captureSourceLabel ?: "non renseigné"})"
                    AudioSourceMode.WAV_ANALYZER ->
                        "Source : fichier WAV — ${provenance.sourceName ?: "non renseigné"}"
                    AudioSourceMode.VIDEO ->
                        "Source : piste audio vidéo — ${provenance.sourceName ?: "non renseigné"}"
                }
            val dfHz = sampleRateHz.toDouble() / fftSize
            val analysisLine =
                String.format(
                    Locale.FRANCE,
                    "Analyse : %d Hz · FFT %d (Δf %.1f Hz) · fenêtre Hann, recouvrement 50 %%",
                    sampleRateHz,
                    fftSize,
                    dfHz,
                )
            val speedLine =
                when {
                    sourceMode == AudioSourceMode.LIVE ->
                        "Vitesse GNSS : filtrée causale (temps réel) · bande d'ordre k=" +
                            String.format(Locale.FRANCE, "%.1f", OrderSearchPolicy.CONFIDENCE_K) + "σ"
                    provenance.speedStatusLabel != null ->
                        "Vitesse GNSS : ${provenance.speedStatusLabel} · bande d'ordre k=" +
                            String.format(Locale.FRANCE, "%.1f", OrderSearchPolicy.CONFIDENCE_K) + "σ"
                    else -> "Vitesse GNSS : aucune télémétrie associée à cette analyse"
                }
            return ReportStamp(
                appVersion = appVersion,
                generatedAt = generatedAt,
                sourceLine = sourceLine,
                analysisLine = analysisLine,
                speedLine = speedLine,
            )
        }
    }
}
