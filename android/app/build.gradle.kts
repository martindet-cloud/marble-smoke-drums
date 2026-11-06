import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.marblesmoke.drums.pocket"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marblesmoke.drums.pocket"
        minSdk = 26
        targetSdk = 35
        // Il workflow passa il numero di esecuzione, così ogni APK aggiorna il precedente.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "0.1"
    }

    // Chiave per l'installazione diretta (sideload): resta uguale fra le build,
    // così gli aggiornamenti si installano sopra la versione precedente.
    // Per il Play Store usare una chiave privata conservata fuori dal repository.
    signingConfigs {
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "marblesmoke"
            keyAlias = "sideload"
            keyPassword = "marblesmoke"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
