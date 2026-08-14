plugins {
    `java-library`
    `maven-publish`
    idea
    `jvm-test-suite`
    id("net.neoforged.moddev") version "2.0.143"
}

version = property("mod_version") as String
group = property("mod_group_id") as String

// Java 17 (used for the 1.20.4 toolchain) still reads source files using the platform's default charset -
// Windows-1252 on Windows - unlike Java 18+, which defaults to UTF-8 everywhere (JEP 400). Without this,
// accented characters in .java string literals get silently corrupted into mojibake at compile time.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // javac's default -Xmaxerrs 100 truncates any port/API-migration pass with more than 100 breakages in
    // one go (e.g. the 1.21.10 port), hiding how much work is actually left. Not runtime-relevant, so no
    // reason to keep it capped.
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

// Gradle's default "reproducible archives" behaviour stamps every zip entry with the DOS-epoch sentinel
// date 1980-02-01 00:00 instead of a real timestamp. NeoForge 20.4.x's mod-file discovery silently drops
// jars whose entries carry that sentinel date - no crash, no error, the file just never appears in the
// candidate list (confirmed empirically: a hand-built jar with real timestamps gets found and processed,
// the exact same content rebuilt through Gradle with the sentinel date does not). Preserve real timestamps
// so the built jar's zip entries look like any normally-authored jar (e.g. Camera mod's own).
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = true
    isReproducibleFileOrder = false
}

// Minecraft itself only requires Java 21 from the 1.20.5 boundary onward - 1.20.4 still runs on Java 17.
// Compiling 1.20.4's sources with a Java 21 toolchain produces class file version 65 bytecode; when Mixin
// weaves one of our classes (e.g. MouseHandlerCursorMixin) into a vanilla target, the merged class inherits
// the higher version stamp, and the actual Java 17 game runtime then refuses to load it with
// UnsupportedClassVersionError - crashing on boot right after the mod is found and its mixins are applied.
val minecraftVersionForToolchain = property("minecraft_version") as String
java.toolchain.languageVersion = JavaLanguageVersion.of(if (minecraftVersionForToolchain.startsWith("1.20")) 17 else 21)

val modId = property("mod_id") as String
val minecraftVersion = property("minecraft_version") as String
val cameraJarVersion = property("camera_jar_version") as String

neoForge {
    // Specify the version of NeoForge to use.
    version = property("neo_version") as String

    // Default run configurations.
    // These can be tweaked, removed, or duplicated as needed.
    runs {
        // Two-player local testing (calls, group chats): run "server" (a dedicated, offline/cracked-mode
        // server - see run-server/server.properties) alongside "client" and "client2", both of which
        // auto-connect to it on launch via --quickPlayMultiplayer, no manual "Direct Connection" click
        // needed. Usernames are fixed offline identities (not the real Microsoft-authenticated LordFinn/
        // Bouteilles accounts - a cracked server can't verify those anyway), each with the standard
        // offline-mode UUID derivation (UUID.nameUUIDFromBytes("OfflinePlayer:<name>")) so they're stable
        // across runs rather than random each launch. Skins still resolve to the real accounts' real skins
        // client-side regardless (see CrazyPhoneInCallScreenScreen's bust rendering / MojangProfileLookup) -
        // that only needs a name, not an authenticated session, since skin data is public. Real Microsoft
        // sessions (for an online-mode server) are handled in-game by In-Game Account Switcher, not here -
        // see the mods folder in run/ and run-client2/.
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            // Per-version, not a shared "run" - each Stonecutter version node (1.20.4, 1.21.1, 1.21.10...)
            // gets its own saves/config/mods folder so switching which version you're testing is just
            // running a different Gradle task, never manual mod-shuffling between versions again.
            gameDirectory = rootProject.file("run-$minecraftVersion")
            programArguments.addAll(
                "--username", "LordFinn", "--uuid", "60b30ab6-a3a3-3980-9bfe-b84bc32ce8d0",
                "--quickPlayMultiplayer", "localhost:25565"
            )
        }

        create("client2") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = rootProject.file("run-$minecraftVersion-client2")
            programArguments.addAll(
                "--username", "Bouteilles", "--uuid", "94f877fb-ab97-3a21-a67f-715f0a12f124",
                "--quickPlayMultiplayer", "localhost:25565"
            )
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = rootProject.file("run-$minecraftVersion-server")
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        // Own gameDirectory, NOT the default "run" the plain client run uses - GameTestServer is a
        // dedicated-server dist, and it otherwise shares "run"'s mods folder, which is meant for CLIENT-side
        // dev conveniences (In-Game Account Switcher, Sodium, Iris) - IAS specifically crashes on that dist
        // ("Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist
        // DEDICATED_SERVER"), discovered the hard way when it landed in "run/mods" for the client run.
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = rootProject.file("run-$minecraftVersion-gametest")
        }

        create("data") {
            data()
            // Matches the original single-module setup: no dedicated gameDirectory override here, so this
            // shares the "client" run's own per-version folder (see gameDirectory above) rather than a new one.
            gameDirectory = rootProject.file("run-$minecraftVersion")

            // Specify the modid for data generation, where to output the resulting resource, and where to look for existing resources.
            programArguments.addAll(
                "--mod", modId, "--all", "--output", rootProject.file("src/generated/resources/").absolutePath,
                "--existing", rootProject.file("src/main/resources/").absolutePath
            )
        }

        // applies to all the run configs above
        configureEach {
            // Recommended logging data for a userdev environment
            // The markers can be added/remove as needed separated by commas.
            // "SCAN": For mods scan.
            // "REGISTRIES": For firing of registry events.
            // "REGISTRYDUMP": For getting the contents of all registries.
            systemProperty("forge.logging.markers", "REGISTRIES")

            // Recommended logging level for the console
            // You can set various levels here.
            // Please read: https://stackoverflow.com/questions/2031163/when-to-use-the-different-log-levels
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        // define mod <-> source bindings
        // these are used to tell the game which sources are for which mod
        // mostly optional in a single mod project
        // but multi mod projects should define one per mod
        create(modId) {
            sourceSet(sourceSets["main"])
        }
    }

    // Runs `src/test/java` tests with the real game bootstrapped (registries, data components, etc.)
    // so tests can use ItemStack/CompoundTag/DataComponents the same way the mod's own runtime code does.
    unitTest {
        testedMod = mods.named(modId)
        enable()
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter("5.10.2")
            dependencies {
                // Only needed for the handful of procedures/helpers that take a LevelAccessor purely to
                // reach registryAccess() (encodeItemStack/decodeItemStack/getGroupMeta) - mocking that one
                // method avoids needing a real ServerLevel just to exercise otherwise-pure NBT logic.
                implementation("org.mockito:mockito-core:5.11.0")
            }
        }
    }
}

// Include resources generated by data generators.
sourceSets.main {
    resources.srcDir(rootProject.file("src/generated/resources"))
}

// GameTest's own annotation-based registration (@GameTestHolder/@PrefixGameTestTemplate/@GameTest) was
// replaced by NeoForge/Mojang with an explicit GameTestInstance/RegisterGameTestsEvent registry model as of
// NeoForge 21.10 - CrazyPhoneGameTests.java targets the old annotation API and isn't shipped in the mod jar
// (dev-only integration coverage), so it's simplest to exclude it from compilation entirely on 1.21.10 rather
// than port it to the new registry model. Follow-up: rewrite it against GameTestInstance if/when needed.
if (minecraftVersion == "1.21.10") {
    sourceSets.main {
        java.exclude("fr/lordfinn/crazyphone/gametest/**")
    }
}

dependencies {
    // Shared libs/ folder at the repo root (not per-version) - one Camera jar per target Minecraft
    // version, all gitignored and dropped in manually by whoever builds this, see README.
    implementation(files(rootProject.file("libs/camera-neoforge-${minecraftVersion}-${cameraJarVersion}.jar")))
    // Optional Simple Voice Chat addon API - compileOnly so the mod doesn't require SVC to be present at
    // runtime; availability is checked via ModList at runtime (see VoicechatIntegration.isAvailable()).
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
}

// SpongePowered Mixin refuses to process a config whose declared compatibilityLevel is higher than the
// JVM actually running the game - Minecraft itself bumped its required Java version from 17 to 21 at the
// 1.20.5 boundary, so crazyphone.mixins.json's compatibilityLevel has to track the same boundary rather
// than being hardcoded to whichever version was scaffolded first.
val mixinCompatibilityLevel = if (minecraftVersion.startsWith("1.20")) "JAVA_17" else "JAVA_21"

// FML's own "javafml" language-loader major version tracks the NeoForge generation, not the mod's own
// version - 1.20.4's NeoForge (20.4.x) ships javafml 2.0.x, while 1.21+ ships javafml 4.x. Declaring
// loaderVersion="[4,)" (the value that's correct for 1.21+) against 1.20.4's actual 2.0 loader makes FML
// silently drop the mod file at discovery time with only a single ERROR log line and no crash screen -
// this is what was actually blocking the 1.20.4 mod jar from ever showing up in the Mods list.
val loaderVersionRange = if (minecraftVersion.startsWith("1.20")) "[2,)" else property("loader_version_range")

// This block of code expands all declared replace properties in the specified resource targets.
// A missing property will result in an error. Properties are expanded using ${} Groovy notation.
val modMetadataProperties = mapOf(
    "minecraft_version" to property("minecraft_version"),
    "minecraft_version_range" to property("minecraft_version_range"),
    "neo_version" to property("neo_version"),
    "neo_version_range" to property("neo_version_range"),
    "loader_version_range" to loaderVersionRange,
    "mod_id" to property("mod_id"),
    "mod_name" to property("mod_name"),
    "mod_license" to property("mod_license"),
    "mod_version" to property("mod_version"),
    "mod_authors" to property("mod_authors"),
    "mod_description" to property("mod_description"),
    "mixin_compatibility_level" to mixinCompatibilityLevel
)
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    inputs.properties(modMetadataProperties)
    expand(modMetadataProperties)
    from(rootProject.file("src/main/templates"))
    into("build/generated/sources/modMetadata")
    // NeoForge only started recognizing "neoforge.mods.toml" (to disambiguate from Forge's own mods.toml
    // in multi-loader jars) from around the 1.21+ generation onward. 1.20.4's NeoForge (20.4.x) mod-file
    // locator only ever looks for the legacy "META-INF/mods.toml" name - a jar shipping neoforge.mods.toml
    // instead is silently classified as an "invalid mod file" with zero log output, never even reaching the
    // point where its content (loaderVersion, etc.) would be read. Confirmed via a full TRACE-level log: the
    // string "neoforge.mods.toml" never appears anywhere in FML's own logging for a 20.4.251 boot.
    if (minecraftVersion.startsWith("1.20")) {
        rename("neoforge.mods.toml", "mods.toml")
    }
}

// Include the output of "generateModMetadata" as an input directory for the build
// this works with both building through Gradle and the IDE.
sourceSets.main {
    resources.srcDir(generateModMetadata)
}
// To avoid having to run "generateModMetadata" manually, make it run on every project reload
neoForge.ideSyncTask(generateModMetadata)

// Example configuration to allow publishing using the maven-publish plugin
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(layout.projectDirectory.dir("repo"))
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
