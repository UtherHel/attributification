package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.ModifierRedirect;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Converts the values of modifiers redirected from a source attribute to its target
 */
@Mixin(EntityAttributeInstance.class)
public abstract class EntityAttributeInstanceMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Convert modifier when added (temporary)
     */
    @Inject(method = "addTemporaryModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierTemp(EntityAttributeModifier modifier, CallbackInfo ci) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            EntityAttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                EntityAttributeInstance self = (EntityAttributeInstance) (Object) this;
                self.addTemporaryModifier(converted);
            }
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * Convert modifier when added (persistent)
     */
    @Inject(method = "addPersistentModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierPersist(EntityAttributeModifier modifier, CallbackInfo ci) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            EntityAttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                EntityAttributeInstance self = (EntityAttributeInstance) (Object) this;
                self.addPersistentModifier(converted);
            }
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * Converts a modifier that reached this (target) instance through a source attribute lookup.
     * Item modifiers are converted when the item's modifiers are built and the target mod's own
     * modifiers are already in target units, so neither is converted here.
     */
    @Unique
    private EntityAttributeModifier convert(EntityAttributeModifier modifier) {
        AttributeMappingEntry mapping = ModifierRedirect.mappingFor((EntityAttributeInstance) (Object) this);
        if (mapping == null) return null;
        
        // Skip conversion for special modes
        if (mapping.isOnlyVisualChange() || mapping.isFullReplacement()) {
            return null;
        }
        
        // Convert value and operation
        EntityAttributeModifier.Operation op = modifier.operation();
        double value = modifier.value();
        boolean changed = false;
        
        // Convert value (only for ADDITION)
        if (op == EntityAttributeModifier.Operation.ADD_VALUE) {
            double newValue = value / mapping.getConversionEquivalence();
            if (newValue != value) {
                value = newValue;
                changed = true;
                AttributificationLogger.info("[EntityAttributeInstance] Converted value: " + 
                    modifier.value() + " -> " + value + " (÷" + mapping.getConversionEquivalence() + ")");
            }
        }
        
        // Convert operation
        if (op == EntityAttributeModifier.Operation.ADD_VALUE) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADD_VALUE -> ADD_MULTIPLIED_BASE");
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADD_VALUE -> ADD_MULTIPLIED_TOTAL");
                    break;
                case DEFAULT:
                default:
                    // No conversion
                    break;
            }
        }
        
        if (!changed) return null;
        
        return new EntityAttributeModifier(
            modifier.id(),
            value,
            op
        );
    }
}
