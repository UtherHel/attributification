package dev.utherhel.attributification.util;

import dev.utherhel.attributification.config.AttributeMappingEntry;
import net.minecraft.entity.attribute.EntityAttributeInstance;

/**
 * Remembers, per thread, the target instance that the last attribute lookup returned for a source
 * attribute ({@code getCustomInstance(source)} redirected to the target). Modifiers added to that
 * instance before the next lookup came from the source and are converted with this mapping.
 * Modifiers added to the target any other way (item modifiers that were already converted, the
 * target mod's own modifiers) are left alone. Every lookup replaces or clears the pending redirect.
 */
public final class ModifierRedirect {

    private static final ThreadLocal<ModifierRedirect> PENDING = new ThreadLocal<>();

    private final EntityAttributeInstance target;
    private final AttributeMappingEntry mapping;

    private ModifierRedirect(EntityAttributeInstance target, AttributeMappingEntry mapping) {
        this.target = target;
        this.mapping = mapping;
    }

    public static void set(EntityAttributeInstance target, AttributeMappingEntry mapping) {
        PENDING.set(new ModifierRedirect(target, mapping));
    }

    public static void clear() {
        PENDING.remove();
    }

    /** The mapping of the pending redirect if it returned {@code instance}, otherwise null. */
    public static AttributeMappingEntry mappingFor(EntityAttributeInstance instance) {
        ModifierRedirect pending = PENDING.get();
        return pending != null && pending.target == instance ? pending.mapping : null;
    }
}
