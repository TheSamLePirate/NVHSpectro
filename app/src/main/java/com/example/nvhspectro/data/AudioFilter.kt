package com.example.nvhspectro.data

import androidx.compose.ui.graphics.Color

data class AudioFilter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: FilterType,
    val minFreq: Int,
    val maxFreq: Int,
    val color: Color
) {
    fun isFrequencyAllowed(f: Double): Boolean {
        return when (type) {
            FilterType.LOW_PASS -> f <= maxFreq
            FilterType.HIGH_PASS -> f >= minFreq
            FilterType.BAND_PASS -> f in minFreq.toDouble()..maxFreq.toDouble()
            FilterType.BAND_STOP -> f !in minFreq.toDouble()..maxFreq.toDouble()
        }
    }
}
