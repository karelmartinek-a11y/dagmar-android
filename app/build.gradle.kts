plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "cz.hcasc.dagmar"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.hcasc.dagmar"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // WebView loads the production web app.
        buildConfigField("String", "DAGMAR_BASE_URL", "\"https://dagmar.hcasc.cz\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Signing: do NOT hardcode secrets in repo.
            // Configure via gradle.properties or environment variables.
            // Example (gradle.properties):
            // DAGMAR_STORE_FILE=/abs/path/keystore.jks
            // DAGMAR_STORE_PASSWORD=...
            // DAGMAR_KEY_ALIAS=dagmar
            // DAGMAR_KEY_PASSWORD=...
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // WebView is part of the Android framework; appcompat provides compatibility.

    // Secure storage for instance_id and instance_token
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Lightweight HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Optional: Kotlin coroutines for async work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON
    implementation("org.json:json:20231013")

    // SplashScreen (Android 12+)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.11.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
