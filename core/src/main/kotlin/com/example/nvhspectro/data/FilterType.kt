package com.example.nvhspectro.data

enum class FilterType {
    LOW_PASS,
    HIGH_PASS,
    BAND_PASS,
    BAND_STOP;

    fun getDisplayName(): String {
        return when (this) {
            LOW_PASS -> "Passe-bas"
            HIGH_PASS -> "Passe-haut"
            BAND_PASS -> "Passe-bande"
            BAND_STOP -> "Coupe-bande"
        }
    }
}
