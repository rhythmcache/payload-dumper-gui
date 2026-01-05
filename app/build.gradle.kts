import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

fun getVersionFromCargoToml(): Triple<Int, Int, Int> {
  val cargoToml = file("../Cargo.toml")
  require(cargoToml.exists()) { "Cargo.toml not found at ${cargoToml.absolutePath}" }

  val lines = cargoToml.readLines()
  val packageIndex = lines.indexOfFirst { it.trim() == "[package]" }
  require(packageIndex != -1) { "[package] section not found in Cargo.toml" }

  val versionLine =
      lines
          .drop(packageIndex + 1)
          .takeWhile { !it.trim().startsWith("[") }
          .find { it.trim().startsWith("version") }
          ?: error("version field not found in [package] section")

  val versionString = versionLine.substringAfter("\"").substringBefore("\"")
  val parts = versionString.split(".")
  require(parts.size == 3) { "Invalid version format: $versionString" }

  return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

val (versionMajor, versionMinor, versionPatch) = getVersionFromCargoToml()

android {
  namespace = "com.rhythmcache.payloaddumper"
  compileSdk = 36

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (keystorePath != null) {
        storeFile = rootProject.file(keystorePath)
        storePassword = System.getenv("RELEASE_STORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  defaultConfig {
    applicationId = "com.rhythmcache.payloaddumper"
    minSdk = 24
    targetSdk = 36
    versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
    versionName = "$versionMajor.$versionMinor.$versionPatch"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "USER_AGENT", "\"PayloadDumper-GUI/$versionName\"")
    buildConfigField("String", "GITHUB_URL", "\"https://github.com/rhythmcache\"")
    buildConfigField("String", "TELEGRAM_URL", "\"https://t.me/rhythmcache\"")
    buildConfigField("String", "REPO_URL", "\"https://github.com/rhythmcache/payload-dumper-gui\"")
    buildConfigField(
        "String",
        "GITHUB_API_URL",
        "\"https://api.github.com/repos/rhythmcache/payload-dumper-gui/releases/latest\"")
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug { isMinifyEnabled = false }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.core.splashscreen)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
