package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.List;

public final class DefaultAttributificationConfig implements AttributificationConfig {
    private LogLevel logLevel = LogLevel.ERRORS;
    private final List<AttributeMappingEntry> mappings = new ArrayList<>();

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setMappings(List<AttributeMappingEntry> mappings) {
        this.mappings.clear();
        this.mappings.addAll(mappings);
    }

    @Override
    public List<AttributeMappingEntry> getMappings() {
        return mappings;
    }
}
