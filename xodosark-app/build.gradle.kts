plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    kotlin("android") version "2.0.0" apply false   // ← version is mandatory
}}