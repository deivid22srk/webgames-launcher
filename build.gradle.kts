// Root build file do WebGames Launcher.
// AGP 9+ traz Kotlin embutido: NÃO aplicamos org.jetbrains.kotlin.android.
// KGP alinhado ao Kotlin embutido do AGP 9.5 (2.4.10).
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/") // fallback resiliente
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
