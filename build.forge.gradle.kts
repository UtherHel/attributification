import me.modmuss50.mpp.ReleaseType
import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

plugins {
    id("net.neoforged.moddev.legacyforge")
    id("me.modmuss50.mod-publish-plugin")
    alias(ft.plugins.default)
}

// DO NOT set group = ...!
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.id"]}-forge"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    else -> JavaVersion.VERSION_16
}

// This can be used for publishing on Modrinth and Curseforge
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

dependencies {
    annotationProcessor("org.spongepowered:mixin:${sc.properties["deps.mixin"]}:processor")
}

legacyForge {
    enable {
        forgeVersion = sc.properties["deps.forge_loader"]
    }

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

mixin {
    add(sourceSets.main.get(), "attributification.refmap.json")
    config("attributification.mixins.json")
}

fletchingTable {
    // Registers classes annotated with `@Mixin` in the mixin config
    mixins.configure(sourceSets.main) {
        mixin("attributification.mixins.json", "default")
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
            register("pack_format", "mod.pack_format")
        }

        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") {
            expand("java" to mixinJava)
            filter { if (it == "{") """{ "refmap": "attributification.refmap.json",""" else it }
        }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "attributification.neoforge.mixins.json")
    }

    jar {
        manifest.attributes("MixinConfigs" to "attributification.mixins.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")

        // Forge recompiling multiple versions in parallel can slow down the system
        usesService(StonecutterTaskMutexService.create(gradle, name))
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", sc.properties["mod.version"])
        from(named<Jar>("reobfJar").flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
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
    file = tasks.named<Jar>("reobfJar").flatMap { it.archiveFile }
    additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
    displayName = "${sc.properties["mod.name"]} Forge ${sc.properties["mod.version"]} for ${sc.current.version}"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG-latest.md")).asText
    modLoaders.add("forge")

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
