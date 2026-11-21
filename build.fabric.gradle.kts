import me.modmuss50.mpp.ReleaseType

plugins {
    // This plugin applies the correct loom variant based on the Minecraft version
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin")
    alias(ft.plugins.default)
    alias(ft.plugins.fabric)
}

// DO NOT set group = ...!
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.id"]}-fabric"

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

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run") // Shares the run directory between versions
        jvmArguments.add("-Dmixin.debug.export=true") // Exports transformed classes for debugging
    }
}

fletchingTable {
    // Registers classes annotated with `@Mixin` in the mixin config
    mixins.configure(sourceSets.main) {
        mixin("attributification.mixins.json", "default")
    }

    // Adds elements annotated with `@Entrypoint` to fabric.mod.json
    fabric.configure(sourceSets.main) {
    }

    // Converts language files in `assets/*/lang/` to Json
    lang.configure(sourceSets.main) {}
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang Mappings on obfuscated versions
    loomx.applyMojangMappings()

    // Use `mod{dependency type}` even on 26.1+ - loom-back-compat converts them
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"]}")
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
    withType<ProcessResources>().configureEach {
        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

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
            put("java", requiredJava.majorVersion)
        }
        inputs.property("java", requiredJava.majorVersion)

        filesMatching(listOf("fabric.mod.json", "pack.mcmeta")) { expand(props) }

        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "attributification.neoforge.mixins.json")
        if (!hasPackMcmeta) exclude("pack.mcmeta")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", sc.properties["mod.version"])
        // loomx.mod(Sources)Jar returns the jar task for the applied loom variant
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
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
    file = loomx.modJar.flatMap { it.archiveFile }
    additionalFiles.from(loomx.modSourcesJar.flatMap { it.archiveFile })
    displayName = "${sc.properties["mod.name"]} Fabric ${sc.properties["mod.version"]} for ${sc.current.version}"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG-latest.md")).asText
    modLoaders.add("fabric")

    if (publishMr) modrinth {
        projectId = sc.properties["publish.modrinth_id"]
        accessToken = modrinthToken
        minecraftVersions.addAll(compatibleVersions)
        requires("fabric-api")
    }

    if (publishCf) curseforge {
        projectId = sc.properties["publish.curseforge_id"]
        accessToken = curseforgeToken
        minecraftVersions.addAll(compatibleVersions)
        client = true
        server = true
        requires("fabric-api")
    }

    if (publishRelease) github {
        accessToken = releaseToken
        parent(rootProject.tasks.named("publishGithub"))
    }
}
