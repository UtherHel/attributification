package dev.utherhel.attributification.neoforge.config;

//? if neoforge {
/*import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.LogLevel;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/^*
 * NeoForge-specific configuration using {@link ModConfigSpec} (TOML).
 * <p>
 * Only stores {@code debug.logLevel}. Attribute mappings are loaded from datapacks.
 * The spec is registered with {@link ModConfig.Type#COMMON} during mod construction.
 ^/
public final class AttributificationNeoForgeConfig
    implements AttributificationConfig
{

    private final ModConfigSpec.ConfigValue<LogLevel> logLevelValue;
    private final ModConfigSpec spec;

    private AttributificationNeoForgeConfig(ModConfigSpec.Builder builder) {
        builder.comment("Debug settings").push("debug");
        this.logLevelValue = builder
            .comment("Log level: NONE, ERRORS, WARNINGS, ALL")
            .defineEnum("logLevel", LogLevel.ERRORS);
        builder.pop();
        this.spec = builder.build();
    }

    /^*
     * Creates the config, builds the spec, and registers it on the given mod container.
     *
     * @param container the mod container to register the config with
     ^/
    public static AttributificationNeoForgeConfig createAndRegister(
        ModContainer container
    ) {
        AttributificationNeoForgeConfig config =
            new AttributificationNeoForgeConfig(new ModConfigSpec.Builder());
        container.registerConfig(ModConfig.Type.COMMON, config.spec);
        return config;
    }

    @Override
    public LogLevel getPlatformLogLevel() {
        return logLevelValue.get();
    }

    /^*
     * Returns the underlying {@link ModConfigSpec} for reference.
     ^/
    public ModConfigSpec getSpec() {
        return spec;
    }
}
*///?}
