package dev.utherhel.attributification.mixin;

//? if >=1.20.5 {
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import dev.utherhel.attributification.util.ModifierRedirect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * Redirects source → target with caching
 */
@Mixin(AttributeMap.class)
public class AttributeContainerMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    // Cache: source -> target (or source if no mapping)
    @Unique
    private static final Map<Holder<Attribute>, Holder<Attribute>> lookupCache = new HashMap<>();
    
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
    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private void redirectAttribute(Holder<Attribute> attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        ModifierRedirect.clear();
        
        isProcessing.set(true);
        try {
            Holder<Attribute> target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeMap self = (AttributeMap) (Object) this;
                
                // CRITICAL: Check if entity actually has the target attribute
                if (!self.hasAttribute(target)) {
                    String sourceId = AttributeUtils.getAttributeId(attribute);
                    String targetId = AttributeUtils.getAttributeId(target);
                    AttributificationLogger.warn("[AttributeContainer] Target attribute does not exist on entity: " + 
                        targetId + " (mapped from " + sourceId + ")");
                    return;
                }
                
                AttributeInstance targetInstance = self.getInstance(target);
                if (targetInstance != null) {
                    AttributificationLogger.info("[AttributeContainer] Redirected getCustomInstance: " + 
                        AttributeUtils.getAttributeId(attribute) + " -> " + AttributeUtils.getAttributeId(target));
                    ModifierRedirect.set(targetInstance,
                        Attributifaction.getAttributeUnifier().getMapping(AttributeUtils.getAttributeId(attribute)));
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
    private void redirectHasAttribute(Holder<Attribute> attribute, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            Holder<Attribute> target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeMap self = (AttributeMap) (Object) this;
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
    private void redirectGetValue(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            AttributeMap self = (AttributeMap) (Object) this;
            
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
            
            Holder<Attribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            
            // CRITICAL: Check if entity actually has the target attribute before trying to access it
            if (!self.hasAttribute(target)) {
                AttributificationLogger.warn("[AttributeContainer] Target attribute does not exist on entity: " + 
                    AttributeUtils.getAttributeId(target) + " (mapped from " + sourceId + ")");
                return;
            }
            
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
    private void redirectGetBaseValue(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
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
            
            Holder<Attribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            
            AttributeMap self = (AttributeMap) (Object) this;
            
            // CRITICAL: Check if entity actually has the target attribute before trying to access it
            if (!self.hasAttribute(target)) {
                AttributificationLogger.warn("[AttributeContainer] Target attribute does not exist on entity: " + 
                    AttributeUtils.getAttributeId(target) + " (mapped from " + sourceId + ")");
                return;
            }
            
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
    private Holder<Attribute> getTargetAttribute(Holder<Attribute> attribute) {
        // Check static lookup cache first
        Holder<Attribute> cachedResult = lookupCache.get(attribute);
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
        Holder<Attribute> target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
        if (target != null && target != attribute) {
            lookupCache.put(attribute, target);
            return target;
        }
        
        lookupCache.put(attribute, attribute);
        return attribute;
    }
}
//?} else {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import dev.utherhel.attributification.util.ModifierRedirect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/^*
 * Redirects source → target with caching
 ^/
@Mixin(AttributeMap.class)
public class AttributeContainerMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    // Cache: source -> target (or source if no mapping)
    @Unique
    private static final Map<Attribute, Attribute> lookupCache = new HashMap<>();
    
    // Register cache clear callback on class load
    static {
        Attributifaction.registerCacheClearCallback(() -> {
            int size = lookupCache.size();
            lookupCache.clear();
            AttributificationLogger.info("Cleared AttributeContainerMixin lookup cache (" + size + " entries)");
        });
    }
    
    /^*
     * getAttribute(source) → getAttribute(target)
     ^/
    @Inject(method = "getInstance(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", at = @At("HEAD"), cancellable = true)
    private void redirectAttribute(Attribute attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        ModifierRedirect.clear();
        
        isProcessing.set(true);
        try {
            Attribute target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeMap self = (AttributeMap) (Object) this;
                AttributeInstance targetInstance = self.getInstance(target);
                if (targetInstance != null) {
                    AttributificationLogger.info("[AttributeContainer] Redirected getCustomInstance: " + 
                        AttributeUtils.getAttributeId(attribute) + " -> " + AttributeUtils.getAttributeId(target));
                    ModifierRedirect.set(targetInstance,
                        Attributifaction.getAttributeUnifier().getMapping(AttributeUtils.getAttributeId(attribute)));
                    cir.setReturnValue(targetInstance);
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /^*
     * hasAttribute(source) → hasAttribute(target)
     ^/
    @Inject(method = "hasAttribute(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Z", at = @At("HEAD"), cancellable = true)
    private void redirectHasAttribute(Attribute attribute, CallbackInfoReturnable<Boolean> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            Attribute target = getTargetAttribute(attribute);
            if (target != null && target != attribute) {
                AttributeMap self = (AttributeMap) (Object) this;
                cir.setReturnValue(self.hasAttribute(target));
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /^*
     * getValue(source) → getValue(target) × equivalence (if bidirectional)
     ^/
    @Inject(method = "getValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D", at = @At("HEAD"), cancellable = true)
    private void redirectGetValue(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            AttributeMap self = (AttributeMap) (Object) this;
            
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
            
            Attribute target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            // Entities without the target attribute keep the source value (vanilla would throw)
            if (!self.hasAttribute(target)) return;
            
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
            isProcessing.set(false);
        }
    }
    
    /^*
     * getBaseValue(source) → getBaseValue(target) × equivalence (if bidirectional)
     ^/
    @Inject(method = "getBaseValue", at = @At("HEAD"), cancellable = true)
    private void redirectGetBaseValue(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
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
            
            Attribute target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == attribute) return;
            
            AttributeMap self = (AttributeMap) (Object) this;
            if (!self.hasAttribute(target)) return;
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
            isProcessing.set(false);
        }
    }
    
    /^*
     * Gets target attribute with caching
     ^/
    @Unique
    private Attribute getTargetAttribute(Attribute attribute) {
        // Check static lookup cache first
        Attribute cachedResult = lookupCache.get(attribute);
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
        Attribute target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
        if (target != null && target != attribute) {
            lookupCache.put(attribute, target);
            return target;
        }
        
        lookupCache.put(attribute, attribute);
        return attribute;
    }
}
*///?}
