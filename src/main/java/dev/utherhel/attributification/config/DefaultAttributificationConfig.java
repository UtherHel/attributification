package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link AttributificationConfig}.
 * <p>
 * Used as a bootstrap config before platform-specific configs are loaded,
 * or as a fallback if no platform config is available.
 * The log level defaults to {@link LogLevel#ERRORS}.
 */
public class DefaultAttributificationConfig implements AttributificationConfig {

    private LogLevel logLevel = LogLevel.ERRORS;

    public DefaultAttributificationConfig() {}

    public DefaultAttributificationConfig(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public LogLevel getPlatformLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }
}
