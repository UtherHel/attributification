package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Converts modifier values and tracks UUIDs for cleanup
 */
@Mixin(EntityAttributeInstance.class)
public abstract class EntityAttributeInstanceMixin {
    
    @Shadow
    private EntityAttribute type;
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Convert modifier when added (temporary)
     */
    @Inject(method = "addTemporaryModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierTemp(EntityAttributeModifier modifier, CallbackInfo ci) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            EntityAttributeModifier converted = convertAndTrack(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                EntityAttributeInstance self = (EntityAttributeInstance) (Object) this;
                self.addTemporaryModifier(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * Convert modifier when added (persistent)
     */
    @Inject(method = "addPersistentModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierPersist(EntityAttributeModifier modifier, CallbackInfo ci) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            EntityAttributeModifier converted = convertAndTrack(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                EntityAttributeInstance self = (EntityAttributeInstance) (Object) this;
                self.addPersistentModifier(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * Untrack modifier when removed
     */
    @Inject(method = "removeModifier(Ljava/util/UUID;)V", at = @At("HEAD"))
    private void untrackModifier(UUID uuid, CallbackInfo ci) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return;
        
        String targetId = AttributeUtils.getAttributeId(this.type);
        if (targetId != null && unifier.isTargetAttribute(targetId)) {
            unifier.untrackRedirectedModifier(targetId, uuid);
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
        unifier.trackRedirectedModifier(mapping.getSourceAttribute(), targetId, modifier.getId());
        
        AttributificationLogger.info("[EntityAttributeInstance] Tracking modifier " + modifier.getId() + 
            " for " + mapping.getSourceAttribute() + " -> " + targetId);
        
        // Skip conversion for special modes
        if (mapping.isOnlyVisualChange() || mapping.isFullReplacement()) {
            return null;
        }
        
        // Convert value and operation
        EntityAttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getValue();
        boolean changed = false;
        
        // Convert value (only for ADDITION)
        if (op == EntityAttributeModifier.Operation.ADDITION) {
            double newValue = value / mapping.getConversionEquivalence();
            if (newValue != value) {
                value = newValue;
                changed = true;
                AttributificationLogger.info("[EntityAttributeInstance] Converted value: " + 
                    modifier.getValue() + " -> " + value + " (÷" + mapping.getConversionEquivalence() + ")");
            }
        }
        
        // Convert operation
        if (op == EntityAttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = EntityAttributeModifier.Operation.MULTIPLY_BASE;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADDITION -> MULTIPLY_BASE");
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = EntityAttributeModifier.Operation.MULTIPLY_TOTAL;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADDITION -> MULTIPLY_TOTAL");
                    break;
                case DEFAULT:
                default:
                    // No conversion
                    break;
            }
        }
        
        if (!changed) return null;
        
        return new EntityAttributeModifier(
            modifier.getId(),
            modifier.getName(),
            value,
            op
        );
    }
}
