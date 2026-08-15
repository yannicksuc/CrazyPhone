plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.20.4"

// Loader identity as a Stonecutter boolean constant, usable in the shared source tree as
// //? if fabric { ... } / //? if neoforge { ... } - exactly the same mechanism already used throughout
// this codebase for Minecraft-version gating (//? if >=1.21.10 etc.), just gating on loader instead. Only
// the two new "*-fabric" nodes carry that suffix (see settings.gradle.kts); every other node is NeoForge.
stonecutter parameters {
    val loader = if (current.project.endsWith("-fabric")) "fabric" else "neoforge"
    constants.match(loader, "fabric", "neoforge")
}