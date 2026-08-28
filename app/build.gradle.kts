plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Single source of truth for the app version [audit B1].
// The About dialog reads BuildConfig.VERSION_NAME; the APK name derives from it.
val appVersionName = "14.0.0"
val appVersionCode = 14

base {
    archivesName.set("APP_NVH_Spectro_v$appVersionName")
}

android {
    namespace = "com.example.nvhspectro"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.nvhspectro"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    lint {
      // [§12, plan 4.4] Localisation and accessibility are build failures, not warnings.
      // HardcodedText only inspects XML layouts, so in this Compose-only app the equivalent
      // check for Kotlin literals lives in ci/checks.sh; this arms the XML side and the
      // checks that DO cover Compose.
      error += listOf("HardcodedText", "ContentDescription", "SetTextI18n", "StringFormatMatches")
      // The project has run with zero lint errors and NO baseline since Phase 0 [DEV-6].
      abortOnError = true
      warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  // Measurement engine (pure Kotlin, JVM-tested) [plan 3.1]
  implementation(project(":core"))
  testImplementation(testFixtures(project(":core")))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Settings/kinematics persistence [S1, plan 3.6]
  implementation(libs.androidx.datastore.preferences)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  // [U8, plan 4.9] Platform splash screen, backported below API 31.
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

}

// StringFormatContractTest reads res/values/strings.xml from the source tree at
// runtime, so Gradle would not otherwise know the test is stale when a string
// changes — the gate would silently skip exactly when it matters. Declare it.
tasks.withType<Test>().configureEach {
  inputs
    .file("src/main/res/values/strings.xml")
    .withPropertyName("stringsXml")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
