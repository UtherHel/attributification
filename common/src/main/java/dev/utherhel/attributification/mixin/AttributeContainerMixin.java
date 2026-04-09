package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Redirects source → target with caching
 */
@Mixin(AttributeContainer.class)
public class AttributeContainerMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    // Cache: source -> target (or source if no mapping)
    @Unique
    private static final Map<RegistryEntry<EntityAttribute>, RegistryEntry<EntityAttribute>> lookupCache = new HashMap<>();
    
    // Register cache clear callback on class load
    static {
        Attributifaction.registerCacheClearCallback(() -> {
            int size = lookupCache.size();
            lookupCache.clear();
            AttributificationLogger.info("Cleared AttributeContainerMixin lookup cache (" + size + " entries)");
        });
    }
    
    /**
     * getAttribute(source) → getAttribute(target)
     */
    @Inject(method = "getCustomInstance", at = @At("HEAD"), cancellable = true)
    private void redirectAttribute(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<EntityAttributeInstance> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            RegistryEntry<EntityAttribute> target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeContainer self = (AttributeContainer) (Object) this;
                EntityAttributeInstance targetInstance = self.getCustomInstance(target);
                if (targetInstance != null) {
                    AttributificationLogger.info("[AttributeContainer] Redirected getCustomInstance: " + 
                        AttributeUtils.getAttributeId(attribute) + " -> " + AttributeUtils.getAttributeId(target));
                    cir.setReturnValue(targetInstance);
                }
            }
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * hasAttribute(source) → hasAttribute(target)
     */
    @Inject(method = "hasAttribute", at = @At("HEAD"), cancellable = true)
    private void redirectHasAttribute(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            RegistryEntry<EntityAttribute> target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeContainer self = (AttributeContainer) (Object) this;
                cir.setReturnValue(self.hasAttribute(target));
            }
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * getValue(source) → getValue(target) × equivalence (if bidirectional)
     */
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void redirectGetValue(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            AttributeContainer self = (AttributeContainer) (Object) this;
            
            // onlyVisualChange: return source value (no redirection)
            if (mapping.isOnlyVisualChange()) {
                // Don't redirect, return as-is
                return;
            }
            
            // fullReplacement: return only base value (freeze source attribute)
            if (mapping.isFullReplacement()) {
                double baseValue = self.getBaseValue(attribute);
                AttributificationLogger.info("[AttributeContainer] Full replacement: " + sourceId + 
                    " frozen at base value " + baseValue);
                cir.setReturnValue(baseValue);
                return;
            }
            
            RegistryEntry<EntityAttribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            
            double targetValue = self.getValue(target);
            
            // unidirectional: return target value directly (no equivalence multiplication)
            if (mapping.isUnidirectional()) {
                AttributificationLogger.info("[AttributeContainer] Unidirectional: " + sourceId + 
                    " = " + targetValue + " (direct from " + AttributeUtils.getAttributeId(target) + ")");
                cir.setReturnValue(targetValue);
                return;
            }
            
            // Bidirectional: Virtual value = (target - subtract) × equivalence
            targetValue -= mapping.getSubtractValue();
            targetValue *= mapping.getEquivalence();
            AttributificationLogger.info("[AttributeContainer] Virtual value: " + sourceId + 
                " = " + targetValue + " (from " + AttributeUtils.getAttributeId(target) + ")");
            
            cir.setReturnValue(targetValue);
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * getBaseValue(source) → getBaseValue(target) × equivalence (if bidirectional)
     */
    @Inject(method = "getBaseValue", at = @At("HEAD"), cancellable = true)
    private void redirectGetBaseValue(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            // onlyVisualChange: return source base value (no redirection)
            if (mapping.isOnlyVisualChange()) {
                // Don't convert, return as-is
                return;
            }
            
            // fullReplacement: return actual base value (no conversion)
            if (mapping.isFullReplacement()) {
                // Don't convert, return as-is
                return;
            }
            
            RegistryEntry<EntityAttribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            
            AttributeContainer self = (AttributeContainer) (Object) this;
            double targetBaseValue = self.getBaseValue(target);
            
            // unidirectional: return target base value directly (no equivalence)
            if (mapping.isUnidirectional()) {
                cir.setReturnValue(targetBaseValue);
                return;
            }
            
            // Bidirectional: apply equivalence
            targetBaseValue -= mapping.getSubtractValue();
            targetBaseValue *= mapping.getEquivalence();
            
            cir.setReturnValue(targetBaseValue);
        } finally {
            isProcessing.remove();
        }
    }
    
    /**
     * Gets target attribute with caching
     */
    @Unique
    private RegistryEntry<EntityAttribute> getTargetAttribute(RegistryEntry<EntityAttribute> attribute) {
        // Check static lookup cache first
        RegistryEntry<EntityAttribute> cachedResult = lookupCache.get(attribute);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        String sourceId = AttributeUtils.getAttributeId(attribute);
        if (sourceId == null || !unifier.shouldUnify(sourceId)) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        AttributeMappingEntry mapping = unifier.getMapping(sourceId);
        if (mapping == null) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        // onlyVisualChange: don't redirect, only rename (handled by AttributeMixin)
        if (mapping.isOnlyVisualChange()) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        // fullReplacement: don't redirect, source is frozen
        if (mapping.isFullReplacement()) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        // unidirectional: don't redirect for hasAttribute/getAttribute
        // Source is NOT considered as target
        if (mapping.isUnidirectional()) {
            lookupCache.put(attribute, attribute);
            return attribute;
        }
        
        // Normal bidirectional mode: redirect to target
        RegistryEntry<EntityAttribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
        if (target != null && target != attribute) {
            lookupCache.put(attribute, target);
            return target;
        }
        
        lookupCache.put(attribute, attribute);
        return attribute;
    }
}
