package dev.utherhel.attributification.mixin;

//? if >=1.21 {
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.ModifierRedirect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Converts the values of modifiers redirected from a source attribute to its target
 */
@Mixin(AttributeInstance.class)
public abstract class EntityAttributeInstanceMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Convert modifier when added (temporary)
     */
    @Inject(method = "addTransientModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierTemp(AttributeModifier modifier, CallbackInfo ci) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                AttributeInstance self = (AttributeInstance) (Object) this;
                self.addTransientModifier(converted);
            }
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * Convert modifier when added (persistent)
     */
    @Inject(method = "addPermanentModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierPersist(AttributeModifier modifier, CallbackInfo ci) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                AttributeInstance self = (AttributeInstance) (Object) this;
                self.addPermanentModifier(converted);
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
    private AttributeModifier convert(AttributeModifier modifier) {
        AttributeMappingEntry mapping = ModifierRedirect.mappingFor((AttributeInstance) (Object) this);
        if (mapping == null) return null;
        
        // Skip conversion for special modes
        if (mapping.isOnlyVisualChange() || mapping.isFullReplacement()) {
            return null;
        }
        
        // Convert value and operation
        AttributeModifier.Operation op = modifier.operation();
        double value = modifier.amount();
        boolean changed = false;
        
        // Convert value (only for ADDITION)
        if (op == AttributeModifier.Operation.ADD_VALUE) {
            double newValue = value / mapping.getConversionEquivalence();
            if (newValue != value) {
                value = newValue;
                changed = true;
                AttributificationLogger.info("[EntityAttributeInstance] Converted value: " + 
                    modifier.amount() + " -> " + value + " (÷" + mapping.getConversionEquivalence() + ")");
            }
        }
        
        // Convert operation
        if (op == AttributeModifier.Operation.ADD_VALUE) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADD_VALUE -> ADD_MULTIPLIED_BASE");
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
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
        
        return new AttributeModifier(
            modifier.id(),
            value,
            op
        );
    }
}
//?} else {
/*import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.ModifierRedirect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Converts the values of modifiers redirected from a source attribute to its target
 ^/
@Mixin(AttributeInstance.class)
public abstract class EntityAttributeInstanceMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /^*
     * Convert modifier when added (temporary)
     ^/
    @Inject(method = "addTransientModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierTemp(AttributeModifier modifier, CallbackInfo ci) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                AttributeInstance self = (AttributeInstance) (Object) this;
                self.addTransientModifier(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /^*
     * Convert modifier when added (persistent)
     ^/
    @Inject(method = "addPermanentModifier", at = @At("HEAD"), cancellable = true)
    private void convertModifierPersist(AttributeModifier modifier, CallbackInfo ci) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeModifier converted = convert(modifier);
            if (converted != null && converted != modifier) {
                ci.cancel();
                AttributeInstance self = (AttributeInstance) (Object) this;
                self.addPermanentModifier(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /^*
     * Converts a modifier that reached this (target) instance through a source attribute lookup.
     * Item modifiers are converted when the item's modifiers are built and the target mod's own
     * modifiers are already in target units, so neither is converted here.
     ^/
    @Unique
    private AttributeModifier convert(AttributeModifier modifier) {
        AttributeMappingEntry mapping = ModifierRedirect.mappingFor((AttributeInstance) (Object) this);
        if (mapping == null) return null;
        
        // Skip conversion for special modes
        if (mapping.isOnlyVisualChange() || mapping.isFullReplacement()) {
            return null;
        }
        
        // Convert value and operation
        AttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getAmount();
        boolean changed = false;
        
        // Convert value (only for ADDITION)
        if (op == AttributeModifier.Operation.ADDITION) {
            double newValue = value / mapping.getConversionEquivalence();
            if (newValue != value) {
                value = newValue;
                changed = true;
                AttributificationLogger.info("[EntityAttributeInstance] Converted value: " + 
                    modifier.getAmount() + " -> " + value + " (÷" + mapping.getConversionEquivalence() + ")");
            }
        }
        
        // Convert operation
        if (op == AttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = AttributeModifier.Operation.MULTIPLY_BASE;
                    changed = true;
                    AttributificationLogger.info("[EntityAttributeInstance] Converted operation: ADDITION -> MULTIPLY_BASE");
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = AttributeModifier.Operation.MULTIPLY_TOTAL;
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
        
        return new AttributeModifier(
            modifier.getId(),
            modifier.getName(),
            value,
            op
        );
    }
}
*///?}
