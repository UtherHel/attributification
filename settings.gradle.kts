pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases/") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots/") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // Check the latest version on https://stonecutter.kikugie.dev/blog/changes/0.10
    id("dev.kikugie.stonecutter") version "0.10"

    // Used for cross-compat for 26.1+ and older versions (https://codeberg.org/KikuGie/loom-back-compat)
    id("dev.kikugie.loom-back-compat") version "0.4.3"

    // Sometimes it is needed to make Gradle run at all, so it doesn't hurt to have
    // (https://github.com/gradle/foojay-toolchains)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// See https://stonecutter.kikugie.dev/wiki/v2/reference/fletching-table/
dependencyResolutionManagement {
    repositories {
        maven("https://maven.kikugie.dev/snapshots/") { name = "KikuGie Snapshots" }
    }

    versionCatalogs {
        create("ft") { from("dev.kikugie.fletching-table:fletching-table.catalog:0.2.0-alpha.14") }
    }
}

stonecutter {
    create(rootProject) {
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // See https://stonecutter.kikugie.dev/wiki/v2/guides/tools/minecraft-changes
        match("26.3", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")
        match("26.1", "fabric", "neoforge")
        match("1.21.1", "fabric", "neoforge")
        match("1.20.1", "fabric", "forge")
    }
}

gradle.lifecycle.beforeProject {
    repositories {
        /**
         * Restricts dependency search of the given [groups] to the [maven URL][url],
         * improving the setup speed.
         */
        fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
            forRepository { maven(url) { name = alias } }
            filter { groups.forEach(::includeGroup) }
        }
        strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
        strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
        strictMaven("https://maven.kikugie.dev/snapshots", "KikuGie Snapshots", "dev.kikugie.fletching-table")
    }
}

rootProject.name = "attributification"
