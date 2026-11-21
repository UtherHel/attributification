package dev.utherhel.attributification.forge.config;

//? if forge {
/*import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.LogLevel;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/^*
 * Forge configuration using {@link ForgeConfigSpec} (TOML).
 * <p>
 * Only stores {@code debug.logLevel}. Attribute mappings are loaded from datapacks.
 * The spec is registered with {@link ModConfig.Type#COMMON} during mod construction.
 ^/
public final class AttributificationForgeConfig implements AttributificationConfig {

    private final ForgeConfigSpec.EnumValue<LogLevel> logLevelValue;
    private final ForgeConfigSpec spec;

    private AttributificationForgeConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Debug and logging configuration").push("debug");
        this.logLevelValue = builder
            .comment("Logging level: NONE, ERRORS, WARNINGS, ALL")
            .defineEnum("logLevel", LogLevel.ERRORS);
        builder.pop();
        this.spec = builder.build();
    }

    public static AttributificationForgeConfig createAndRegister() {
        AttributificationForgeConfig config = new AttributificationForgeConfig(new ForgeConfigSpec.Builder());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, config.spec);
        return config;
    }

    /^* Until Forge has loaded the file, the default level is used. ^/
    @Override
    public LogLevel getPlatformLogLevel() {
        return spec.isLoaded() ? logLevelValue.get() : LogLevel.ERRORS;
    }
}
*///?}
