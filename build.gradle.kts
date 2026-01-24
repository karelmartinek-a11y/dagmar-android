// Top-level build script for DAGMAR Android app
// Kotlin DSL (Gradle). No external services; dependencies fetched only at build time.

plugins {
    // Versions are aligned with a stable Android Gradle Plugin that supports modern Kotlin.
    // Kept explicit for deterministic builds.
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// No repositories here; use settings.gradle.kts for centralized repo management.

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
