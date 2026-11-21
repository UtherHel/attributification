package dev.utherhel.attributification.forge.config;

import dev.utherhel.attributification.config.LogLevel;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AttributificationForgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<LogLevel> LOG_LEVEL;

    static {
        BUILDER.comment("Debug and logging configuration").push("debug");
        LOG_LEVEL = BUILDER
            .comment("Logging level: NONE, ERRORS, WARNINGS, ALL")
            .defineEnum("logLevel", LogLevel.ERRORS);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private AttributificationForgeConfig() {}

    public static ForgeConfigSpec getSpec() {
        return SPEC;
    }

    public static LogLevel getLogLevel() {
        return LOG_LEVEL.get();
    }
}
