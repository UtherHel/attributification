package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
@Mixin(Attribute.class)
public class AttributeMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Changes translation key: source → target name
     */
    @Inject(method = "getDescriptionId", at = @At("HEAD"), cancellable = true)
    private void changeTranslationKey(CallbackInfoReturnable<String> cir) {
        if (Boolean.TRUE.equals(isProcessing.get())) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            Attribute self = (Attribute) (Object) this;
            String sourceId = AttributeUtils.getAttributeIdDirect(self);
            
            if (sourceId == null || !unifier.shouldUnify(sourceId)) return;
            
            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) return;
            
            // Get target attribute using AttributeUtils
            Holder<Attribute> targetEntry = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (targetEntry == null) return;
            
            Attribute target = AttributeUtils.getValue(targetEntry);
            if (target == null || target == self) return;
            
            // Return target's translation key
            cir.setReturnValue(target.getDescriptionId());
        } finally {
            isProcessing.remove();
        }
    }
}
