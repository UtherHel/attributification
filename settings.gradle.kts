pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases/") { name = "KikuGie Releases" }
    }
}

plugins {
    // https://stonecutter.kikugie.dev/blog/changes/0.10
    id("dev.kikugie.stonecutter") version "0.10"

    // Picks the Loom variant that matches the Minecraft version (https://codeberg.org/KikuGie/loom-back-compat)
    id("dev.kikugie.loom-back-compat") version "0.4.3"

    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        match("1.21.1", "fabric", "neoforge")
        match("1.20.1", "fabric", "forge")
    }
}

gradle.lifecycle.beforeProject {
    repositories {
        // Restricts dependency search of the given [groups] to the [maven URL][url].
        fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
            forRepository { maven(url) { name = alias } }
            filter { groups.forEach(::includeGroup) }
        }
        strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
        strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    }
}

rootProject.name = "attributification"
