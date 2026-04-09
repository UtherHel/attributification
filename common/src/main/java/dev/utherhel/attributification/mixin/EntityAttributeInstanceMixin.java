package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Converts modifier values and tracks Identifiers for cleanup
 */
@Mixin(EntityAttributeInstance.class)
public abstract class EntityAttributeInstanceMixin {
    
    @Shadow
    private RegistryEntry<EntityAttribute> type;
    
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
            EntityAttributeModifier converted = convertAndTrack(modifier);
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
            EntityAttributeModifier converted = convertAndTrack(modifier);
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
     * Converts modifier value and tracks UUID
     */
    @Unique
    private EntityAttributeModifier convertAndTrack(EntityAttributeModifier modifier) {
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return null;
        
        String targetId = AttributeUtils.getAttributeId(this.type);
        if (targetId == null || !unifier.isTargetAttribute(targetId)) return null;
        
        // Fast lookup using AttributeUnifier's reverse cache
        AttributeMappingEntry mapping = unifier.getMappingByTarget(targetId);
        if (mapping == null) return null;
        
        // Track UUID (always track, even if no conversion)
        unifier.trackRedirectedModifier(mapping.getSourceAttribute(), targetId, modifier.id());
        
        AttributificationLogger.info("[EntityAttributeInstance] Tracking modifier " + modifier.id() + 
            " for " + mapping.getSourceAttribute() + " -> " + targetId);
        
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
