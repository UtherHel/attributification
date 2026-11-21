plugins {
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
}

stonecutter active "1.21.1-fabric"

// https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Version- and loader-specific values from `stonecutter.properties.toml`
    properties {
        tags(version, loader)
    }

    // Loader constants for Stonecutter comments (`//? if fabric {`)
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }
}
