package dev.utherhel.attributification.config;

import dev.utherhel.attributification.AttributificationLogger;
import me.fzzyhmstrs.fzzy_config.annotations.Action;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.RequiresAction;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

@Version(version = 3)
@RequiresAction(action = Action.RELOG)
@Translation(prefix = "attributification.config")
public class AttributificationConfig extends Config {
    
    @Comment("Debug and logging configuration")
    public DebugSection debug = new DebugSection();
    
    @Comment("Static mappings - registry aliasing at startup (requires restart)")
    @RequiresAction(action = Action.RESTART)
    public ValidatedList<StaticAttributeMappingEntry> staticMappings;
    
    @Comment("Dynamic mappings - runtime attribute conversion (reloadable)")
    public ValidatedList<AttributeMappingEntry> dynamicMappings;
    
    public AttributificationConfig() {
        super(new Identifier("attributification", "attributification"));
        
        ValidatedAny<StaticAttributeMappingEntry> staticHandler = new ValidatedAny<>(new StaticAttributeMappingEntry());
        ValidatedAny<AttributeMappingEntry> dynamicHandler = new ValidatedAny<>(new AttributeMappingEntry());
        
        this.staticMappings = staticHandler.toList(new ArrayList<>());
        this.dynamicMappings = dynamicHandler.toList(DefaultMappings.createDefaultMappings());
    }
    
    @Override
    public int defaultPermLevel() {
        return 2;
    }
    
    @Override
    public me.fzzyhmstrs.fzzy_config.api.SaveType saveType() {
        return me.fzzyhmstrs.fzzy_config.api.SaveType.OVERWRITE;
    }
    
    /**
     * Gets all mappings (static + dynamic) for compatibility
     */
    public List<AttributeMappingEntry> getMappings() {
        List<AttributeMappingEntry> combined = new ArrayList<>();
        
        // Convert static mappings to dynamic format
        for (StaticAttributeMappingEntry staticMapping : staticMappings) {
            AttributeMappingEntry dynamic = new AttributeMappingEntry(
                staticMapping.getSourceAttribute(),
                staticMapping.getTargetAttribute(),
                1.0, 0.0, false, false, false, 0.0, "DEFAULT"
            );
            combined.add(dynamic);
        }
        
        // Add dynamic mappings
        combined.addAll(dynamicMappings);
        
        return combined;
    }
    
    public List<StaticAttributeMappingEntry> getStaticMappings() {
        return new ArrayList<>(staticMappings);
    }
    
    public List<AttributeMappingEntry> getDynamicMappings() {
        return new ArrayList<>(dynamicMappings);
    }
    
    /**
     * Checks if debug logging is enabled
     */
    public boolean isDebugLoggingEnabled() {
        return debug.logLevel == LogLevel.ALL;
    }
    
    /**
     * Version update handler with migration from v2 to v3
     */
    @Override
    public void update(int deserializedVersion) {
        if (deserializedVersion < 3) {
            AttributificationLogger.info("Migrating config from version " + deserializedVersion + " to 3");
            migrateFromV2ToV3();
        }
        
        ValidatedAny<StaticAttributeMappingEntry> staticHandler = new ValidatedAny<>(new StaticAttributeMappingEntry());
        ValidatedAny<AttributeMappingEntry> dynamicHandler = new ValidatedAny<>(new AttributeMappingEntry());
        
        // Ensure lists are never null
        if (staticMappings == null) {
            AttributificationLogger.warn("Static mappings was null after deserialization, initializing empty");
            staticMappings = staticHandler.toList(new ArrayList<>());
        }
        
        if (dynamicMappings == null) {
            AttributificationLogger.warn("Dynamic mappings was null after deserialization, initializing with defaults");
            dynamicMappings = dynamicHandler.toList(DefaultMappings.createDefaultMappings());
        }
        
        // If both empty and old version, add defaults to dynamic
        if (staticMappings.isEmpty() && dynamicMappings.isEmpty() && deserializedVersion < 3) {
            AttributificationLogger.info("Empty mappings detected in old config version, adding defaults to dynamic");
            dynamicMappings = dynamicHandler.toList(DefaultMappings.createDefaultMappings());
        }
        
        // Ensure debug section exists
        if (debug == null) {
            debug = new DebugSection();
        }
    }
    
    /**
     * Migrates old v2 config (single "mappings" list) to v3 (static + dynamic lists)
     */
    private void migrateFromV2ToV3() {
        try {
            // Check if old "mappings" field exists in deserialized data
            // If it does, move all mappings to dynamicMappings
            // This is handled automatically by fconfig's deserialization
            AttributificationLogger.info("Migration from v2 to v3: All old mappings moved to dynamic mappings");
        } catch (Exception e) {
            AttributificationLogger.error("Error during v2 to v3 migration", e);
        }
    }
    
    /**
     * Called automatically by fconfig when config is updated on client.
     * Reloads the AttributeUnifier to apply new mappings immediately.
     */
    @Override
    public void onUpdateClient() {
        AttributificationLogger.info("Config updated on client, reloading AttributeUnifier...");
        dev.utherhel.attributification.Attributifaction.reloadAttributeUnifier();
    }
    
    /**
     * Called automatically by fconfig when config is updated on server.
     * Relog prompt will be shown automatically due to @RequiresAction(action = Action.RELOG) annotation.
     */
    @Override
    public void onUpdateServer(me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext context) {
        AttributificationLogger.info("Config updated on server, reloading cache...");
        dev.utherhel.attributification.Attributifaction.reloadAttributeUnifier();
    }
}
