package com.example.nvhspectro.data

enum class KinematicsInputMode(val label: String) {
    V1000("V1000 Direct"),
    GEAR_RATIO("Rapport Global"),
    DETAILED_CHAIN("Chaîne Détaillée")
}

/** Serializable so the painstakingly-entered chain survives process death [S1, plan 3.6]. */
@kotlinx.serialization.Serializable
data class KinematicsConfig(
    val isEnabled: Boolean = false,
    val inputMode: KinematicsInputMode = KinematicsInputMode.V1000,
    val v1000Kmh: Double = 10.0,            // Vitesse en km/h pour 1000 RPM
    val globalGearRatio: Double = 9.5,        // Rapport total de réduction
    val gearReductionRatio: Double = 3.2,     // Rapport réducteur / descente
    val axleRatio: Double = 3.0,              // Rapport de pont
    val tireWidthMm: Int = 205,               // Largeur du pneu en mm (ex: 205)
    val tireAspectRatio: Int = 55,            // Hauteur du flanc en % (ex: 55)
    val rimDiameterInches: Int = 16,          // Diamètre de jante en pouces (ex: 16)
    val wheelRadiusMeters: Double = 0.31,     // Rayon sous charge de secours si renseigné directement
    val vehicleName: String = "",             // Identification du véhicule
    val motorName: String = "",               // Identification du moteur / GMPe
    val comments: String = "",                // Notes d'essai
    val holdTimeSec: Double = 3.0,            // Durée de rémanence visuelle des étiquettes (secondes)
    val selectedTrackedOrder: Double = 18.0,  // Ordre spécifique sélectionné pour traçage 2D (ex: H18)
    val targetHarmonicsText: String = ""      // Harmoniques cibles / liste blanche (ex: "7.4, 18, 22.2, 36")
) {
    /**
     * Retourne la liste des ordres cibles renseignés par l'utilisateur (ex: [7.4, 18.0, 22.2, 36.0]).
     * Si la chaîne est vide, retourne une liste vide (mode détection ouverte).
     */
    fun parsedTargetOrders(): List<Double> {
        if (targetHarmonicsText.isBlank()) return emptyList()
        // [C11] "22,5" is a French decimal, not the two orders 22 and 5. Rule:
        // if the text uses no '.' anywhere, a comma directly between digits is a
        // decimal; otherwise commas are separators (dots carry the decimals).
        val normalized = if (!targetHarmonicsText.contains('.')) {
            targetHarmonicsText.replace(Regex("(?<=\\d),(?=\\d)"), ".")
        } else {
            targetHarmonicsText
        }
        return normalized
            .split(',', ';', ' ', '\n')
            .mapNotNull { token ->
                val cleaned = token.trim().removePrefix("H").removePrefix("h")
                cleaned.toDoubleOrNull()
            }
            .filter { it > 0.0 }
    }
    /**
     * Calcule le rayon dynamique sous charge de la roue (en mètres) à partir des dimensions pneu vendeur.
     */
    fun calculateWheelRadiusMeters(): Double {
        if (tireWidthMm <= 0 || tireAspectRatio <= 0 || rimDiameterInches <= 0) {
            return wheelRadiusMeters.coerceAtLeast(0.1)
        }
        val rimDiameterMeters = rimDiameterInches * 0.0254
        val sidewallHeightMeters = (tireWidthMm * (tireAspectRatio / 100.0)) / 1000.0
        val totalWheelDiameterMeters = rimDiameterMeters + (2.0 * sidewallHeightMeters)
        val freeRadiusMeters = totalWheelDiameterMeters / 2.0
        // Rayon efficace sous charge avec affaissement moyen du flanc (~1.5%)
        return freeRadiusMeters * 0.985
    }

    /**
     * Calcule la V1000 équivalente en km/h pour 1000 RPM selon le mode de saisie sélectionné.
     */
    fun getEffectiveV1000(): Double {
        val effectiveRadius = calculateWheelRadiusMeters()
        return when (inputMode) {
            KinematicsInputMode.V1000 -> v1000Kmh.coerceAtLeast(0.1)
            KinematicsInputMode.GEAR_RATIO -> {
                val wheelRpm = 1000.0 / globalGearRatio.coerceAtLeast(0.01)
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * effectiveRadius) / 60.0
                wheelSpeedMs * 3.6
            }
            KinematicsInputMode.DETAILED_CHAIN -> {
                val totalRatio = (gearReductionRatio * axleRatio).coerceAtLeast(0.01)
                val wheelRpm = 1000.0 / totalRatio
                val wheelSpeedMs = (wheelRpm * 2.0 * Math.PI * effectiveRadius) / 60.0
                wheelSpeedMs * 3.6
            }
        }
    }

    /**
     * Calcule le régime moteur (RPM) pour une vitesse donnée en km/h.
     */
    fun calculateRpm(speedKmh: Float): Double {
        val v1000 = getEffectiveV1000()
        if (v1000 <= 0.0) return 0.0
        return (speedKmh.toDouble() / v1000) * 1000.0
    }

    /**
     * Calcule la fréquence fondamentale H1 en Hz (RPM / 60).
     */
    fun calculateH1FreqHz(speedKmh: Float): Double {
        val rpm = calculateRpm(speedKmh)
        return rpm / 60.0
    }
}

/**
 * Balise d'harmonique active avec timestamp de persistance pour rémanence visuelle.
 */
data class TrackedHarmonicTag(
    val orderName: String,         // ex: "H18", "H36"
    val orderValue: Double,        // ex: 18.0
    val freqHz: Int,
    val ttnrDb: Double,
    val absDbFS: Double,
    val speedKmh: Float,
    val rpm: Double,
    val binIndex: Int,
    val lastSeenTimestampMs: Long
)

/**
 Entrée accumulée pour le rapport synthétique d'émergences.
 */
data class EmergenceReportEntry(
    val orderName: String,         // ex: "H18", "H22.2", "H14.8"
    val orderValue: Double,        // ex: 18.0, 22.2, 14.8
    var minSpeedKmh: Float,
    var maxSpeedKmh: Float,
    var minRpm: Int,
    var maxRpm: Int,
    var minFreqHz: Int,
    var maxFreqHz: Int,
    var maxEmergenceDb: Double,
    var countDetections: Int = 1,
    var lastTimestampMs: Long = System.currentTimeMillis()
)

data class ManualOrderAnchor(
    val frameIndex: Int,
    val binIndex: Int,
    val isUserPlaced: Boolean = true,
    val exactBinF: Float = binIndex.toFloat()
)
