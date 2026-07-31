package dev.utherhel.attributification.config;

public enum LogLevel {
    NONE,
    ERRORS,
    WARNINGS,
    ALL;

    public boolean shouldLogError() {
        return this != NONE;
    }

    public boolean shouldLogWarning() {
        return this == WARNINGS || this == ALL;
    }

    public boolean shouldLogInfo() {
        return this == ALL;
    }
}
