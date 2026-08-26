package com.example.nvhspectro.data

/**
 * [C11, plan 1.8] Locale-tolerant numeric input: French keyboards produce
 * comma decimals, which `toDoubleOrNull()` rejects — and silent fallbacks to
 * defaults then corrupted every downstream RPM/H1/order computation.
 */
fun String.toFlexibleDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
