package com.example.nvhspectro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification of the kinematics math — Phase 0.6 of the AAA plan.
 *
 * Expected values are HAND-COMPUTED from the physical formulas, not derived by
 * calling the code under test, so these tests genuinely verify the math:
 *
 *   205/55 R16: rim 16 in = 0.40640 m; sidewall 205 mm × 0.55 = 0.11275 m
 *   free diameter = 0.40640 + 2×0.11275 = 0.63190 m → free radius 0.315950 m
 *   loaded radius = 0.315950 × 0.985 = 0.31121075 m
 *   circumference = 2π × 0.31121075 = 1.9553952 m
 */
class KinematicsConfigTest {

    private val tire205_55R16 = KinematicsConfig(
        tireWidthMm = 205, tireAspectRatio = 55, rimDiameterInches = 16
    )

    // --- Wheel geometry -------------------------------------------------

    @Test
    fun wheelRadius_205_55_R16_matchesHandComputation() {
        assertEquals(0.31121075, tire205_55R16.calculateWheelRadiusMeters(), 1e-7)
    }

    @Test
    fun wheelRadius_invalidTireDims_fallsBackToDirectRadius() {
        val c = KinematicsConfig(tireWidthMm = 0, wheelRadiusMeters = 0.29)
        assertEquals(0.29, c.calculateWheelRadiusMeters(), 1e-9)
    }

    @Test
    fun wheelRadius_fallback_isFlooredAt10cm() {
        val c = KinematicsConfig(tireWidthMm = 0, wheelRadiusMeters = 0.0)
        assertEquals(0.1, c.calculateWheelRadiusMeters(), 1e-9)
    }

    // --- V1000 across the three input modes -----------------------------

    @Test
    fun v1000_directMode_returnsInput() {
        val c = KinematicsConfig(inputMode = KinematicsInputMode.V1000, v1000Kmh = 12.5)
        assertEquals(12.5, c.getEffectiveV1000(), 1e-9)
    }

    @Test
    fun v1000_directMode_flooredAtPointOne() {
        val c = KinematicsConfig(inputMode = KinematicsInputMode.V1000, v1000Kmh = 0.0)
        assertEquals(0.1, c.getEffectiveV1000(), 1e-9)
    }

    @Test
    fun v1000_gearRatioMode_ratio9_5_tire205_55R16() {
        // wheel rpm = 1000 / 9.5 = 105.263158
        // v = 105.263158 × 1.9553952 / 60 m/s × 3.6 = 12.3498 km/h
        val c = tire205_55R16.copy(
            inputMode = KinematicsInputMode.GEAR_RATIO, globalGearRatio = 9.5
        )
        assertEquals(12.3498, c.getEffectiveV1000(), 5e-3)
    }

    @Test
    fun v1000_detailedChainMode_3_2x3_0_tire205_55R16() {
        // total ratio 9.6 → wheel rpm 104.16667
        // v = 104.16667 × 1.9553952 / 60 × 3.6 = 12.2212 km/h
        val c = tire205_55R16.copy(
            inputMode = KinematicsInputMode.DETAILED_CHAIN,
            gearReductionRatio = 3.2, axleRatio = 3.0
        )
        assertEquals(12.2212, c.getEffectiveV1000(), 5e-3)
    }

    // --- RPM and H1 -----------------------------------------------------

    @Test
    fun rpm_50kmh_atV1000of10_is5000() {
        val c = KinematicsConfig(inputMode = KinematicsInputMode.V1000, v1000Kmh = 10.0)
        assertEquals(5000.0, c.calculateRpm(50f), 1e-6)
    }

    @Test
    fun h1_is_rpmOver60() {
        val c = KinematicsConfig(inputMode = KinematicsInputMode.V1000, v1000Kmh = 10.0)
        assertEquals(5000.0 / 60.0, c.calculateH1FreqHz(50f), 1e-6)
    }

    // --- Target-orders parsing ------------------------------------------

    @Test
    fun parsedTargetOrders_standardList() {
        val c = KinematicsConfig(targetHarmonicsText = "7.4, 18, 22.2, 36")
        assertEquals(listOf(7.4, 18.0, 22.2, 36.0), c.parsedTargetOrders())
    }

    @Test
    fun parsedTargetOrders_acceptsHPrefixAndMixedSeparators() {
        val c = KinematicsConfig(targetHarmonicsText = "H18; h36\n7.4")
        assertEquals(listOf(18.0, 36.0, 7.4), c.parsedTargetOrders())
    }

    @Test
    fun parsedTargetOrders_blankIsEmpty_zeroAndNegativeFiltered() {
        assertTrue(KinematicsConfig(targetHarmonicsText = "  ").parsedTargetOrders().isEmpty())
        assertEquals(
            listOf(3.0),
            KinematicsConfig(targetHarmonicsText = "0, -2, 3").parsedTargetOrders()
        )
    }

    /**
     * [C11, plan 1.8 — FIXED, formerly pinned] French decimal commas parse as
     * decimals when the text contains no '.'; with dots present, commas stay
     * separators (the historical list syntax).
     */
    @Test
    fun c11_frenchDecimalComma_parsesAsDecimal() {
        assertEquals(listOf(22.5), KinematicsConfig(targetHarmonicsText = "22,5").parsedTargetOrders())
        assertEquals(
            listOf(7.4, 18.0, 22.2),
            KinematicsConfig(targetHarmonicsText = "7,4 18 22,2").parsedTargetOrders()
        )
    }

    @Test
    fun c11_dotDecimals_keepCommaAsSeparator() {
        assertEquals(
            listOf(7.4, 18.0, 22.2, 36.0),
            KinematicsConfig(targetHarmonicsText = "7.4, 18, 22.2, 36").parsedTargetOrders()
        )
    }

    @Test
    fun c11_flexibleDoubleParsing_acceptsBothDecimalMarks() {
        assertEquals(9.5, "9,5".toFlexibleDoubleOrNull()!!, 1e-9)
        assertEquals(9.5, " 9.5 ".toFlexibleDoubleOrNull()!!, 1e-9)
        assertEquals(null, "abc".toFlexibleDoubleOrNull())
        assertEquals(null, "".toFlexibleDoubleOrNull())
    }
}
