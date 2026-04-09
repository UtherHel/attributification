package dev.utherhel.attributification.config;

import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.ConfigSection;

@Translation(prefix = "attributification.config.debug")
public class DebugSection extends ConfigSection {
    
    @Comment("Logging level: NONE, ERRORS, WARNINGS, ALL")
    public LogLevel logLevel = LogLevel.ERRORS;
}
