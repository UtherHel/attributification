package dev.utherhel.attributification.mixin;

//? if >=1.20.5 {
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import java.util.function.BiConsumer;
//? if >=1.21.6 {
/*import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.apache.commons.lang3.function.TriConsumer;
*///?}
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Converts source → target item modifiers (Fabric): tooltips and equipment
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Unique
    private static final ThreadLocal<Boolean> isProcessing =
        ThreadLocal.withInitial(() -> false);

    //? if >=1.21.6 {
    /*@ModifyArg(
        method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V"
        ),
        index = 1
    )
    private TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> wrapConsumerForTooltip(
        TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> original
    ) {
        // Since 1.21.6 the tooltip path carries a Display per modifier; convert like the BiConsumer path and keep it
        if (!Attributifaction.isFabric()) return original;
        if (Boolean.TRUE.equals(isProcessing.get())) return original;
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return original;
        return (attribute, modifier, display) -> {
            isProcessing.set(true);
            try {
                processModifier(attribute, modifier, (a, mod) -> original.accept(a, mod, display), unifier);
            } finally {
                isProcessing.remove();
            }
        };
    }
    *///?} else if >=1.21 {
    /**
     * Wraps the consumer to convert source → target for tooltip display
     * This targets the AttributeModifiersComponent.applyModifiers call inside applyAttributeModifier
     */
    @ModifyArg(
        method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V"
        ),
        index = 1
    )
    private BiConsumer<
        Holder<Attribute>,
        AttributeModifier
    > wrapConsumerForTooltip(
        BiConsumer<
            Holder<Attribute>,
            AttributeModifier
        > original
    ) {
        return wrapBiConsumer(original);
    }
    //?}

    /**
     * Same conversion for equipment: entities apply item modifiers through
     * applyAttributeModifiers(EquipmentSlot, ...), not the tooltip method above.
     * Before 1.21 the tooltip also goes through this method, slot by slot.
     */
    @ModifyArg(
        method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"
        ),
        //? if neoforge && <1.21 {
        /*// NeoForge 20.x replaces this call with ItemAttributeModifierEvent (handled in AttributificationNeoForgeEvents)
        require = 0,
        *///?}
        index = 1
    )
    private BiConsumer<
        Holder<Attribute>,
        AttributeModifier
    > wrapConsumerForEquipment(
        BiConsumer<
            Holder<Attribute>,
            AttributeModifier
        > original
    ) {
        return wrapBiConsumer(original);
    }

    @Unique
    private BiConsumer<Holder<Attribute>, AttributeModifier> wrapBiConsumer(
        BiConsumer<Holder<Attribute>, AttributeModifier> original
    ) {
        // Only run on Fabric; NeoForge uses NeoForgeAttributeUtilMixin for tooltip conversion
        if (!Attributifaction.isFabric()) return original;
        if (Boolean.TRUE.equals(isProcessing.get())) return original;

        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return original;

        return (attribute, modifier) -> {
            isProcessing.set(true);
            try {
                processModifier(attribute, modifier, original, unifier);
            } finally {
                isProcessing.remove();
            }
        };
    }

    /**
     * Processes a single modifier and applies conversion
     */
    @Unique
    private void processModifier(
        Holder<Attribute> sourceAttr,
        AttributeModifier modifier,
        BiConsumer<
            Holder<Attribute>,
            AttributeModifier
        > consumer,
        AttributeUnifier unifier
    ) {
        String sourceId = AttributeUtils.getAttributeId(sourceAttr);
        if (sourceId == null || !unifier.shouldUnify(sourceId)) {
            consumer.accept(sourceAttr, modifier);
            return;
        }

        AttributeMappingEntry mapping = unifier.getMapping(sourceId);
        if (mapping == null) {
            consumer.accept(sourceAttr, modifier);
            return;
        }

        Holder<Attribute> targetAttr =
            AttributeUtils.getFirstAvailableAttribute(
                mapping.getTargetAttributes()
            );
        if (targetAttr == null) {
            consumer.accept(sourceAttr, modifier);
            return;
        }

        applyMapping(
            sourceId,
            sourceAttr,
            targetAttr,
            modifier,
            mapping,
            consumer
        );
    }

    /**
     * Applies mapping to modifier
     */
    @Unique
    private void applyMapping(
        String sourceId,
        Holder<Attribute> sourceAttr,
        Holder<Attribute> targetAttr,
        AttributeModifier modifier,
        AttributeMappingEntry mapping,
        BiConsumer<
            Holder<Attribute>,
            AttributeModifier
        > consumer
    ) {
        if (mapping.isOnlyVisualChange()) {
            AttributificationLogger.info(
                "[ItemStack] Visual change only: " +
                    sourceId +
                    " -> " +
                    AttributeUtils.getAttributeId(targetAttr)
            );
            consumer.accept(targetAttr, modifier);
            return;
        }

        AttributeModifier convertedModifier = convertModifier(
            modifier,
            mapping
        );
        AttributificationLogger.info(
            "[ItemStack] Converted tooltip: " +
                sourceId +
                " -> " +
                AttributeUtils.getAttributeId(targetAttr) +
                ", value: " +
                modifier.amount() +
                " -> " +
                convertedModifier.amount()
        );
        consumer.accept(targetAttr, convertedModifier);
    }

    /**
     * Converts modifier for tooltip display
     */
    @Unique
    private AttributeModifier convertModifier(
        AttributeModifier modifier,
        AttributeMappingEntry mapping
    ) {
        AttributeModifier.Operation op = modifier.operation();
        double value = modifier.amount();

        // Convert value (only for ADD_VALUE)
        if (op == AttributeModifier.Operation.ADD_VALUE) {
            value = value / mapping.getConversionEquivalence();
        }

        // Convert operation
        if (op == AttributeModifier.Operation.ADD_VALUE) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    break;
                case DEFAULT:
                default:
                    break;
            }
        }

        //? if >=1.21 {
        return new AttributeModifier(modifier.id(), value, op);
        //?} else {
        /*return new AttributeModifier(modifier.id(), modifier.name(), value, op);
        *///?}
    }
}
//?} else {
/*import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/^*
 * Shows target attribute in tooltip with converted values
 ^/
@Mixin(ItemStack.class)
public class ItemStackMixin {
    
    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    
    /^*
     * Converts source → target for tooltip display
     ^/
    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void convertTooltip(EquipmentSlot slot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        Boolean processing = isProcessing.get();
        if (Boolean.TRUE.equals(processing)) return;
        
        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) return;
            
            Multimap<Attribute, AttributeModifier> original = cir.getReturnValue();
            if (original.isEmpty()) return;
            
            Multimap<Attribute, AttributeModifier> converted = convertAttributes(original, unifier);
            if (converted != null) {
                cir.setReturnValue(converted);
            }
        } finally {
            isProcessing.set(false);
        }
    }
    
    /^*
     * Converts all attributes in the multimap
     ^/
    @Unique
    private Multimap<Attribute, AttributeModifier> convertAttributes(
            Multimap<Attribute, AttributeModifier> original, AttributeUnifier unifier) {
        Multimap<Attribute, AttributeModifier> converted = HashMultimap.create();
        boolean hasChanges = false;
        
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            if (processAttributeEntry(entry, unifier, converted)) {
                hasChanges = true;
            }
        }
        
        return hasChanges ? converted : null;
    }
    
    /^*
     * Processes a single attribute entry
     * @return true if changes were made
     ^/
    @Unique
    private boolean processAttributeEntry(Map.Entry<Attribute, AttributeModifier> entry,
                                          AttributeUnifier unifier,
                                          Multimap<Attribute, AttributeModifier> converted) {
        Attribute sourceAttr = entry.getKey();
        AttributeModifier modifier = entry.getValue();
        
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
        
        Attribute targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
        if (targetAttr == null) {
            converted.put(sourceAttr, modifier);
            return false;
        }
        
        applyMapping(sourceId, sourceAttr, targetAttr, modifier, mapping, converted);
        return true;
    }
    
    /^*
     * Applies mapping to modifier
     ^/
    @Unique
    private void applyMapping(String sourceId, Attribute sourceAttr, Attribute targetAttr,
                             AttributeModifier modifier, AttributeMappingEntry mapping,
                             Multimap<Attribute, AttributeModifier> converted) {
        if (mapping.isOnlyVisualChange()) {
            AttributificationLogger.info("[ItemStack] Visual change only: " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr));
            converted.put(targetAttr, modifier);
            return;
        }
        
        AttributeModifier convertedModifier = convertModifier(modifier, mapping);
        AttributificationLogger.info("[ItemStack] Converted tooltip: " + sourceId + " -> " + 
            AttributeUtils.getAttributeId(targetAttr) + ", value: " + modifier.getAmount() + " -> " + convertedModifier.getAmount());
        converted.put(targetAttr, convertedModifier);
    }
    
    /^*
     * Converts modifier for tooltip display
     ^/
    @Unique
    private AttributeModifier convertModifier(AttributeModifier modifier, AttributeMappingEntry mapping) {
        AttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getAmount();
        
        // Convert value (only for ADDITION)
        if (op == AttributeModifier.Operation.ADDITION) {
            value = value / mapping.getConversionEquivalence();
        }
        
        // Convert operation
        if (op == AttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = AttributeModifier.Operation.MULTIPLY_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = AttributeModifier.Operation.MULTIPLY_TOTAL;
                    break;
                case DEFAULT:
                default:
                    break;
            }
        }
        
        return new AttributeModifier(
            modifier.getId(),
            //? if >=1.20.3 {
            modifier.save().getString("Name"),
            //?} else {
            /^modifier.getName(),
            ^///?}
            value,
            op
        );
    }
}
*///?}
