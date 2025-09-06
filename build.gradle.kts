// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Cambia la versión a 8.2.0 para que coincida
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
    }
}