// Build script for the "1.20.1" subproject only - real, original net.minecraftforge:forge:1.20.1-47.x,
// predating NeoForge's own fork/rebrand (net.minecraftforge.* -> net.neoforged.neoforge.*, which happened
// at the 1.20.2 boundary). Built via ModDevGradle's own "legacyforge" plugin/DSL (net.neoforged.moddev.legacyforge
// / legacyForge{}) rather than the modern "neoForge{}" block build.gradle.kts uses for every other node -
// NeoForge's own tooling doesn't even publish a "neoforge" artifact for 1.20.1 at all (confirmed against
// the real maven-metadata.xml: no 1.20.1-line versions under net.neoforged:neoforge, only under the
// original net.minecraftforge:forge coordinate) - this is the officially blessed way to keep building a
// 1.20.1 mod from within the same modern NeoForge Gradle tooling every other node already uses.
//
// Mirrors build.gradle.kts's own structure/conventions (generateModMetadata, mixin compat level, jar
// timestamp fix, dev-launch's own client/client2/server run shape) wherever they still apply to exactly
// one Minecraft version - the many "if (minecraftVersion == ...)"/">=26"/etc. branches build.gradle.kts
// carries for the other 8 nodes it serves are simply omitted here rather than copied and left dead.
plugins {
    `java-library`
    `maven-publish`
    idea
    `jvm-test-suite`
    id("net.neoforged.moddev.legacyforge") version "2.0.91"
}

version = property("mod_version") as String
group = property("mod_group_id") as String

// Same reasoning as build.gradle.kts's own identical block - Java 17 still reads .java source files using
// the platform's default charset (Windows-1252 on Windows) unless told otherwise, corrupting accented
// characters in string literals at compile time.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xmaxerrs")
    options.compilerArgs.add("10000")
}

repositories {
    mavenLocal()
    maven("https://maven.maxhenkel.de/repository/public")
}

base {
    archivesName = property("mod_id") as String
}

// Same reproducible-archives timestamp fix as build.gradle.kts - see that file's own doc comment on why
// NeoForge's (and, by inheritance through the same ModDevGradle mod-file locator, legacyforge's) mod
// discovery silently drops a jar whose zip entries carry the DOS-epoch sentinel date.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = true
    isReproducibleFileOrder = false
}

// Mojang ships Java 17 to end users on 1.20.1.
java.toolchain.languageVersion = JavaLanguageVersion.of(17)

val modId = property("mod_id") as String

val thisProject = project

legacyForge {
    // Specify the version of Forge to use - real Forge, not a "neoforge" artifact (see this file's own
    // top-of-file doc comment on why 1.20.1 has no such thing).
    version = "${property("minecraft_version")}-${property("forge_version")}"

    parchment {
        mappingsVersion = property("parchment_mappings_version") as String
        minecraftVersion = property("parchment_minecraft_version") as String
    }

    // Same shape/reasoning as build.gradle.kts's own runs block - see that file's own doc comments on the
    // client/client2/server split (two-player local testing, quickPlayMultiplayer auto-connect, offline
    // UUIDs) and the dedicated gameTestServer/data run dirs. legacyForge's runs{} DSL exposes the identical
    // client()/server()/data()/programArgument(s)/systemProperty/gameDirectory surface as neoForge's own -
    // confirmed against the real NeoForgeMDKs/MDK-Forge-1.20.1-ModDevGradle reference template.
    runs {
        create("client") {
            client()
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = thisProject.file("run")
            programArguments.addAll(
                "--username", "LordFinn", "--uuid", "60b30ab6-a3a3-3980-9bfe-b84bc32ce8d0",
                "--quickPlayMultiplayer", "localhost:25565"
            )
        }

        create("client2") {
            client()
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = thisProject.file("run-client2")
            programArguments.addAll(
                "--username", "Bouteilles", "--uuid", "94f877fb-ab97-3a21-a67f-715f0a12f124",
                "--quickPlayMultiplayer", "localhost:25565"
            )
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = thisProject.file("run-server")
        }

        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = thisProject.file("run-gametest")
        }

        create("data") {
            data()
            gameDirectory = thisProject.file("run")
            programArguments.addAll(
                "--mod", modId, "--all", "--output", rootProject.file("src/generated/resources/").absolutePath,
                "--existing", rootProject.file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
        }
    }

    // Unlike neoForge{}, the legacyForge{} extension has no unitTest{} DSL at all (ModDevGradle never
    // backported that feature to its old-Forge support) - see build.legacyforge.gradle.kts's own doc
    // comment on the testing{} block below for how `src/test/java` is handled on this version instead.
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter("5.10.2")
            dependencies {
                implementation("org.mockito:mockito-core:5.11.0")
            }
        }
    }
}

sourceSets.main {
    resources.srcDir(rootProject.file("src/generated/resources"))
}

// CrazyPhoneGameTests.java is written against NeoForge's own (<1.21.10) annotation-based GameTest
// registration (@GameTestHolder/@PrefixGameTestTemplate) - a NeoForge-only rewrite of Forge's original
// SimpleGameTestServer-driven discovery, itself already replaced again by 21.10's GameTestInstance registry
// (see build.gradle.kts's own exclusion for that boundary). Real, original Forge 1.20.1 has neither: porting
// this dev-only integration-test file (not shipped in the mod jar either way) to yet a third gametest API
// isn't worth it for coverage that has no effect on players - excluded from compilation entirely instead.
sourceSets.main {
    java.exclude("fr/lordfinn/crazyphone/gametest/**")
}

dependencies {
    // Optional Simple Voice Chat addon API - compileOnly so the mod doesn't require SVC to be present at
    // runtime; availability is checked via ModList at runtime (see VoicechatIntegration.isAvailable()).
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
}

// This block of code expands all declared replace properties in the specified resource targets - same
// "generate per-version file content in Kotlin, not in the tracked resource itself" idea build.gradle.kts's
// own generateModMetadata uses.
val modMetadataProperties = mapOf(
    "minecraft_version_range" to property("minecraft_version_range"),
    // The mod's own dependency block declares "requires modid=forge at this version range" - real Forge's
    // own version numbering (47.x), not a NeoForge one, and modId is "forge" (see loader_mod_id below), not
    // "neoforge" - see neoforge.mods.toml's own doc comment on this token.
    "neo_version_range" to property("forge_version_range"),
    "loader_version_range" to property("loader_version_range"),
    "loader_mod_id" to "forge",
    "mod_id" to property("mod_id"),
    "mod_name" to property("mod_name"),
    "mod_license" to property("mod_license"),
    "mod_version" to property("mod_version"),
    "mod_authors" to property("mod_authors"),
    "mod_description" to property("mod_description"),
    // Real Forge on 1.20.1 predates the 1.20.5 Java-version bump entirely - always JAVA_17, no version
    // branching needed the way build.gradle.kts's own mixinCompatibilityLevel has to do across 9 nodes.
    "mixin_compatibility_level" to "JAVA_17",
    // Real, original Forge 1.20.1's mods.toml schema predates NeoForge's own "type" (string enum) rework of
    // this field entirely - it's still the older "mandatory" (boolean) name. Confirmed the hard way: leaving
    // "type = ..." in produced "InvalidModFileException: Missing required field mandatory in dependency" at
    // actual server startup (FML's own internal mod-file validation, not a compile-time check).
    "dependency_required" to "mandatory = true",
    "dependency_optional" to "mandatory = false"
)
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    inputs.properties(modMetadataProperties)
    expand(modMetadataProperties)
    from(rootProject.file("src/main/templates"))
    into("build/generated/sources/modMetadata")
    // Real Forge (like NeoForge 20.4.x - see build.gradle.kts's own doc comment on this exact rename) only
    // ever looks for the legacy "META-INF/mods.toml" name, never "neoforge.mods.toml".
    rename("neoforge.mods.toml", "mods.toml")
}
sourceSets.main {
    resources.srcDir(generateModMetadata)
}
legacyForge.ideSyncTask(generateModMetadata)

// Real, original Forge on 1.20.1 fails to build a valid ResourcePackInfo for this mod's dev-mode "folder
// mod" source location (logged as "File Mod File: .../build/classes/java/main failed to load a valid
// ResourcePackInfo") because that location has no pack.mcmeta at all - unlike NeoForge's own ModDevGradle
// tooling for the other targets (which auto-synthesizes one), old Forge apparently needs a real file.
// Consequences confirmed live, both silent (Forge's default forge-client.toml has showLoadWarnings=true,
// which swallows the resulting ModLoadingWarning into a LoadingErrorScreen instead of logging it):
//  - the mod's own resource pack is dropped from ReloadableResourceManager entirely (crazy_phone item
//    models/sounds "not found" even though the files are genuinely present on disk and correctly rooted)
//  - the client silently stalls on a LoadingErrorScreen instead of ever reaching the title screen, which
//    also means quickPlayMultiplayer's auto-connect (scheduled to fire once the initial screen is set)
//    never runs
// Scoped to this buildscript only (a dedicated task instead of adding to the shared src/main/templates
// pool build.gradle.kts's own generateModMetadata also draws from) because pack_format is genuinely
// version-specific and 1.20.1 is the only node confirmed to need this - build.gradle.kts's other 8 nodes
// already work fine without one.
val packMetadataDir = layout.buildDirectory.dir("generated/sources/packMetadata")
val packMetadataModName = property("mod_name") as String
val generatePackMetadata = tasks.register("generatePackMetadata") {
    val outputDir = packMetadataDir
    val modName = packMetadataModName
    outputs.dir(outputDir)
    doLast {
        outputDir.get().asFile.mkdirs()
        outputDir.get().file("pack.mcmeta").asFile.writeText(
            "{\"pack\":{\"pack_format\":15,\"description\":\"$modName resources\"}}"
        )
    }
}
sourceSets.main {
    resources.srcDir(generatePackMetadata)
}

// The photo-dyeing recipe's own "minecraft:crafting_dye" type doesn't exist before 26.x - see
// build.gradle.kts's own identical block for the full explanation. Dyeing still works on 1.20.1 via the
// item's own "dyeable" tag + vanilla's built-in ArmorDyeRecipe.
//
// crazy_phone.json's own "result" object uses the post-1.20.5 Data Components shape ({"id": ..., "count":
// ...}) - real 1.20.1's RecipeManager expects the old {"item": ..., "count": ...} shape instead and fails
// the whole recipe file with "Parsing error loading recipe crazyphone:crazy_phone" /
// JsonSyntaxException("Missing item, expected to find a string") otherwise, confirmed live at server
// startup (non-fatal - the server keeps running - but the crafting recipe silently doesn't exist). Also
// strips the unrecognized "neoforge:conditions"/"fabric:load_conditions" toggle (CrazyPhoneCraftingCondition
// itself is already skipped for legacyforge - see ModItems.java's own doc comment - so the recipe is simply
// always enabled here, same as every other loader when the config toggle is on).
tasks.named<ProcessResources>("processResources") {
    doLast {
        destinationDir.resolve("data/crazyphone/recipe/crazy_phone_photo_dyed.json").delete()
        val craftingRecipe = destinationDir.resolve("data/crazyphone/recipes/crazy_phone.json")
        val json = groovy.json.JsonSlurper().parse(craftingRecipe) as MutableMap<String, Any?>
        json.remove("neoforge:conditions")
        json.remove("fabric:load_conditions")
        @Suppress("UNCHECKED_CAST")
        val result = json["result"] as MutableMap<String, Any?>
        result["item"] = result.remove("id")
        craftingRecipe.writeText(groovy.json.JsonOutput.toJson(json))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
