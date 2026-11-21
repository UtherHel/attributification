package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import java.util.function.BiConsumer;
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
        // Only run on Fabric; NeoForge uses NeoForgeAttributeUtilMixin for tooltip conversion
        if (!Attributifaction.isFabric()) return original;
        if (Boolean.TRUE.equals(isProcessing.get())) return original;

        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return original;

        // Return wrapped consumer that converts attributes
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
     * Same conversion for equipment: entities apply item modifiers through
     * applyAttributeModifiers(EquipmentSlot, ...), not the tooltip method above
     */
    @ModifyArg(
        method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"
        ),
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
        return wrapConsumerForTooltip(original);
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

        return new AttributeModifier(modifier.id(), value, op);
    }
}
