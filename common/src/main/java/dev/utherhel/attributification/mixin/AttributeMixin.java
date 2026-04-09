package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.EntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OPTIMIZED: Changes translation key for all mappings
 * Uses AttributeUtils for shared caching
 * Source attribute always shows target's name
 */
@Mixin(EntityAttribute.class)
public class AttributeMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Changes translation key: source → target name
     */
    @Inject(method = "getTranslationKey", at = @At("RETURN"), cancellable = true)
    private void changeTranslationKey(CallbackInfoReturnable<String> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            EntityAttribute self = (EntityAttribute) (Object) this;
            String sourceId = AttributeUtils.getAttributeId(self);
            
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            // Get target attribute using AttributeUtils
            EntityAttribute target = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (target == null || target == self) return;
            
            // Return target's translation key
            cir.setReturnValue(target.getTranslationKey());
        } finally {
            isProcessing.set(false);
        }
    }
}
