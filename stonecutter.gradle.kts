import me.modmuss50.mpp.ReleaseType

plugins {
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    id("co.uzzu.dotenv.gradle") version "4.0.0"
}

stonecutter active "1.21.1-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Makes version- and loader-specific properties apply from `stonecutter.properties.toml`
    properties {
        tags(version, loader)
    }

    // Adds constants to Stonecutter comments (i.e. for `//? if fabric {...`)
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    swaps["mod_version"] = "\"${properties["mod.version"]}\";"
    swaps["minecraft"] = "\"${current.version}\";"

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}

publishMods {
    val token: String? = env.fetchOrNull("GITHUB_TOKEN")
    dryRun = token == null || sc.properties.getAs<Boolean>("publish.dry_run")

    type = ReleaseType.STABLE
    version = sc.properties["mod.version"]
    displayName = "${sc.properties["mod.name"]} ${sc.properties["mod.version"]}"
    changelog = providers.fileContents(layout.projectDirectory.file("CHANGELOG-latest.md")).asText

    github {
        accessToken = token
        repository = "UtherHel/attributification"
        commitish = "main"
        tagName = "v${sc.properties["mod.version"]}"
        allowEmptyFiles = true
    }
}

stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}
