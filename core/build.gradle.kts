// :core — the measurement engine [plan 3.1, audit A1].
//
// Pure Kotlin/JVM: ZERO Android imports allowed in this module (ci/checks.sh
// enforces it). DSP (FFT/TTNR), order tracking, kinematics, timeline mapping
// and the speed estimator live here so they are testable in seconds on the JVM.
// This module carries the project's line-coverage gate (plan §1: ≥ 90 %).
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kover)
  `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

kover {
    reports {
        verify {
            rule("core line coverage ≥ 90% [plan §1 Testing gate]") {
                minBound(90)
            }
        }
    }
}

dependencies {
  implementation(libs.jtransforms)

  testImplementation(libs.junit)
}
