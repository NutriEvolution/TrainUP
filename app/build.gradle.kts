plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

val vName: String = (project.findProperty("versionName") as String?) ?: "0.0.1"
val vCode: Int = vName.split(".").map { it.toIntOrNull() ?: 0 }.let { p ->
  (p.getOrElse(0) { 0 }) * 10000 + (p.getOrElse(1) { 0 }) * 100 + (p.getOrElse(2) { 0 })
}.coerceAtLeast(1)

android {
  namespace = "it.prencipe.ripresaforza"
  compileSdk = 34
  defaultConfig {
    applicationId = "it.prencipe.ripresaforza"
    minSdk = 26
    targetSdk = 34
    versionCode = vCode
    versionName = vName
  }
  signingConfigs {
    create("release") {
      val ks = System.getenv("KEYSTORE_PATH")
      if (ks != null) {
        storeFile = file(ks)
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
    }
  }
  buildFeatures { buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.activity:activity-ktx:1.9.2")
}
