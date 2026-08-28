package com.example.nvhspectro.data

import androidx.compose.ui.graphics.Color

/**
 * A manually-validated tracked order (report mode). Lives in :app, not :core,
 * because its display color is a Compose type [plan 3.1].
 */
data class SmartTrackedOrder(
    val name: String,
    val color: Color,
    val path: List<ManualOrderAnchor>,
    val minRpm: Int?,
    val maxRpm: Int?,
    val minSpeedKmh: Float?,
    val maxSpeedKmh: Float?,
    val minFreqHz: Int,
    val maxFreqHz: Int,
    val maxEmergenceDb: Double
)
