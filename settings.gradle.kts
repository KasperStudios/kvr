pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    create(rootProject) {
        // Root `src/` is the platform-independent KVR core (:common)
        versions("1.20.1", "1.21.1", "1.21.11")
        branch("fabric")
        // NeoForge did not exist for 1.20.1
        branch("neoforge") { versions("1.21.1", "1.21.11") }
    }
}

rootProject.name = "kvr"
