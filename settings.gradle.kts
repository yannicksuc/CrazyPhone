pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

// The 3 existing nodes (1.20.4, 1.21.1, 1.21.10) stay on the default "build.gradle.kts" template and
// keep building NeoForge exactly as before - untouched. The two "-fabric" nodes below are NEW: same
// Minecraft-version comparison semantics (second constructor arg) for the //? if >=/< predicates, but a
// separate "build.fabric.gradle.kts" template (Fabric Loom instead of NeoForge's moddev plugin) - see
// stonecutter.gradle.kts for how source files tell the two loaders apart (//? if fabric / //? if neoforge).
stonecutter {
    shared {
        versions("1.20.4", "1.21.1", "1.21.10")
        version("1.21.1-fabric", "1.21.1").buildscript("build.fabric.gradle.kts")
        version("1.20.1-fabric", "1.20.1").buildscript("build.fabric.gradle.kts")
        vcsVersion = "1.21.1"
    }
    create(rootProject)
}
