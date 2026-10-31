plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Distinct from both the old snapshot (com.codex.mobile) and the
    // 2.0.514 release (gptos.intelligence.assistant). This is a new
    // reconstruction, not a re-signing of either one.
    namespace = "app.anyclaw.reconstruction"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.anyclaw.reconstruction"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-reconstruction"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
