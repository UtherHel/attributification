import me.modmuss50.mpp.ReleaseType
import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

plugins {
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin")
    alias(ft.plugins.default)
}

// DO NOT set group = ...!
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.id"]}-neoforge"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// This can be used for publishing on Modrinth and Curseforge
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

// 1.21.9+ data packs use min_format/max_format; the loaders do not need pack.mcmeta in a mod jar
val hasPackMcmeta: Boolean = sc.current.parsed < "1.21.9"

neoForge {
    version = sc.properties["deps.neo_loader"]

    mods {
        register(sc.properties["mod.id"]) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run") // Shares the run directory between versions
            client()
        }

        register("server") {
            gameDirectory = rootProject.file("run")
            server()
        }
    }
}

fletchingTable {
    // Registers classes annotated with `@Mixin` in the mixin config;
    // classes annotated with `@MixinEnvironment("neoforge")` go to the NeoForge-only config
    mixins.configure(sourceSets.main) {
        mixin("attributification.mixins.json", "default")
        mixin("attributification.neoforge.mixins.json", "neoforge")
    }

    // Converts language files in `assets/*/lang/` to Json
    lang.configure(sourceSets.main) {}
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            register("loader", "mod.loader_compat")
            if (hasPackMcmeta) register("pack_format", "mod.pack_format")
        }

        filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "META-INF/mods.toml")
        if (!hasPackMcmeta) exclude("pack.mcmeta")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")

        // NeoForge recompiling multiple versions in parallel can slow down the system
        usesService(StonecutterTaskMutexService.create(gradle, name))
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", sc.properties["mod.version"])
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${sc.properties["mod.version"]}"))
    }
}

publishMods {
    val modrinthToken: String? = env.fetchOrNull("MODRINTH_TOKEN")
    val publishMr: Boolean = sc.properties.getAs("publish.modrinth")
    val curseforgeToken: String? = env.fetchOrNull("CURSEFORGE_TOKEN")
    val publishCf: Boolean = sc.properties.getAs("publish.curseforge")
    val releaseToken: String? = env.fetchOrNull("GITHUB_TOKEN")
    val publishRelease: Boolean = sc.properties.getAs("publish.github")

    dryRun = sc.properties.getAs<Boolean>("publish.dry_run")
        || (publishMr && modrinthToken == null)
        || (publishCf && curseforgeToken == null)
        || (publishRelease && releaseToken == null)
    type = ReleaseType.of(sc.properties["publish.status"])
    file = tasks.jar.flatMap { it.archiveFile }
    additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
    displayName = "${sc.properties["mod.name"]} NeoForge ${sc.properties["mod.version"]} for ${sc.current.version}"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG-latest.md")).asText
    modLoaders.add("neoforge")

    if (publishMr) modrinth {
        projectId = sc.properties["publish.modrinth_id"]
        accessToken = modrinthToken
        minecraftVersions.addAll(compatibleVersions)
    }

    if (publishCf) curseforge {
        projectId = sc.properties["publish.curseforge_id"]
        accessToken = curseforgeToken
        minecraftVersions.addAll(compatibleVersions)
        client = true
        server = true
    }

    if (publishRelease) github {
        accessToken = releaseToken
        parent(rootProject.tasks.named("publishGithub"))
    }
}
