package dev.utherhel.attributification.forge.config;

//? if forge || (neoforge && <1.20.5) {
/*import dev.utherhel.attributification.config.AttributificationConfig;
import dev.utherhel.attributification.config.LogLevel;
//? if forge {
/^import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
^///?} else {
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

/^*
 * Forge configuration using {@link ForgeConfigSpec} (TOML).
 * <p>
 * Only stores {@code debug.logLevel}. Attribute mappings are loaded from datapacks.
 * The spec is registered with {@link ModConfig.Type#COMMON} during mod construction.
 ^/
public final class AttributificationForgeConfig implements AttributificationConfig {

    //? if forge {
    /^private final ForgeConfigSpec.EnumValue<LogLevel> logLevelValue;
    ^///?} else {
    private final ModConfigSpec.EnumValue<LogLevel> logLevelValue;
    //?}
    //? if forge {
    /^private final ForgeConfigSpec spec;
    ^///?} else {
    private final ModConfigSpec spec;
    //?}

    //? if forge {
    /^private AttributificationForgeConfig(ForgeConfigSpec.Builder builder) {
    ^///?} else {
    private AttributificationForgeConfig(ModConfigSpec.Builder builder) {
    //?}
        builder.comment("Debug and logging configuration").push("debug");
        this.logLevelValue = builder
            .comment("Logging level: NONE, ERRORS, WARNINGS, ALL")
            .defineEnum("logLevel", LogLevel.ERRORS);
        builder.pop();
        this.spec = builder.build();
    }

    public static AttributificationForgeConfig createAndRegister() {
        //? if forge {
        /^AttributificationForgeConfig config = new AttributificationForgeConfig(new ForgeConfigSpec.Builder());
        ^///?} else {
        AttributificationForgeConfig config = new AttributificationForgeConfig(new ModConfigSpec.Builder());
        //?}
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
