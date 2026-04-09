package dev.utherhel.attributification.config;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;
import org.jetbrains.annotations.NotNull;

public enum LogLevel implements EnumTranslatable {
    NONE,
    ERRORS,
    WARNINGS,
    ALL;
    
    @NotNull
    @Override
    public String prefix() {
        return "attributification.config.logLevel";
    }
    
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
