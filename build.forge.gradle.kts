import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

plugins {
    id("net.neoforged.moddev.legacyforge")
}

// DO NOT set group = ...!
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.id"]}-forge"

val requiredJava = JavaVersion.VERSION_17

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
