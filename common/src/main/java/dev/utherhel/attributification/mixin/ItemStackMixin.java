package dev.utherhel.attributification.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Shows target attribute in tooltip with converted values
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /**
     * Converts source → target for tooltip display
     */
    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void convertTooltip(EquipmentSlot slot, CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            Multimap<EntityAttribute, EntityAttributeModifier> original = cir.getReturnValue();
            if (original.isEmpty()) return;
            
            Multimap<EntityAttribute, EntityAttributeModifier> converted = convertAttributes(original, unifier);
            if (converted != null) {
                cir.setReturnValue(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * Converts all attributes in the multimap
     */
    @Unique
    private Multimap<EntityAttribute, EntityAttributeModifier> convertAttributes(
            Multimap<EntityAttribute, EntityAttributeModifier> original, AttributeUnifier unifier) {
        Multimap<EntityAttribute, EntityAttributeModifier> converted = HashMultimap.create();
        boolean hasChanges = false;
        
        for (Map.Entry<EntityAttribute, EntityAttributeModifier> entry : original.entries()) {
            if (processAttributeEntry(entry, unifier, converted)) {
                hasChanges = true;
            }
        }
        
        return hasChanges ? converted : null;
    }
    
    /**
     * Processes a single attribute entry
     * @return true if changes were made
     */
    @Unique
    private boolean processAttributeEntry(Map.Entry<EntityAttribute, EntityAttributeModifier> entry,
                                          AttributeUnifier unifier,
                                          Multimap<EntityAttribute, EntityAttributeModifier> converted) {
        EntityAttribute sourceAttr = entry.getKey();
        EntityAttributeModifier modifier = entry.getValue();
        
        String sourceId = AttributeUtils.getAttributeId(sourceAttr);
        if (sourceId == null || !unifier.shouldUnify(sourceId)) {
            converted.put(sourceAttr, modifier);
            return false;
        }
        
        AttributeMappingEntry mapping = unifier.getMapping(sourceId);
        if (mapping == null) {
            converted.put(sourceAttr, modifier);
            return false;
        }
        
        EntityAttribute targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
        if (targetAttr == null) {
            converted.put(sourceAttr, modifier);
            return false;
        }
        
        applyMapping(sourceId, sourceAttr, targetAttr, modifier, mapping, converted);
        return true;
    }
    
    /**
     * Applies mapping to modifier
     */
    @Unique
    private void applyMapping(String sourceId, EntityAttribute sourceAttr, EntityAttribute targetAttr,
                             EntityAttributeModifier modifier, AttributeMappingEntry mapping,
                             Multimap<EntityAttribute, EntityAttributeModifier> converted) {
        if (mapping.isFullReplacement()) {
            AttributificationLogger.info("[ItemStack] Hiding modifier for fullReplacement: " + sourceId);
            return;
        }
        
        if (mapping.isOnlyVisualChange()) {
            AttributificationLogger.info("[ItemStack] Visual change only: " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr));
            converted.put(targetAttr, modifier);
            return;
        }
        
        EntityAttributeModifier convertedModifier = convertModifier(modifier, mapping);
        AttributificationLogger.info("[ItemStack] Converted tooltip: " + sourceId + " -> " + 
            AttributeUtils.getAttributeId(targetAttr) + ", value: " + modifier.getValue() + " -> " + convertedModifier.getValue());
        converted.put(targetAttr, convertedModifier);
    }
    
    /**
     * Converts modifier for tooltip display
     */
    @Unique
    private EntityAttributeModifier convertModifier(EntityAttributeModifier modifier, AttributeMappingEntry mapping) {
        EntityAttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getValue();
        
        // Convert value (only for ADDITION)
        if (op == EntityAttributeModifier.Operation.ADDITION) {
            value = value / mapping.getConversionEquivalence();
        }
        
        // Convert operation
        if (op == EntityAttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = EntityAttributeModifier.Operation.MULTIPLY_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = EntityAttributeModifier.Operation.MULTIPLY_TOTAL;
                    break;
                case DEFAULT:
                default:
                    break;
            }
        }
        
        return new EntityAttributeModifier(
            modifier.getId(),
            modifier.getName(),
            value,
            op
        );
    }
}
