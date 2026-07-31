package dev.utherhel.attributification.config;

import java.util.List;

public interface AttributificationConfig {
    LogLevel getLogLevel();
    List<AttributeMappingEntry> getMappings();
}
