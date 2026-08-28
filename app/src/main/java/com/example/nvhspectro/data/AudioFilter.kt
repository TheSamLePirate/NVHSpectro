package com.example.nvhspectro.data

import androidx.compose.ui.graphics.Color

data class AudioFilter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: FilterType,
    val minFreq: Int,
    val maxFreq: Int,
    val color: Color
)
