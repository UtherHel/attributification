package dev.utherhel.attributification.mixin;

import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import java.util.function.BiConsumer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Shows target attribute in tooltip with converted values
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
        method = "applyAttributeModifier",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/component/type/AttributeModifiersComponent;applyModifiers(Lnet/minecraft/component/type/AttributeModifierSlot;Ljava/util/function/BiConsumer;)V"
        ),
        index = 1
    )
    private BiConsumer<
        RegistryEntry<EntityAttribute>,
        EntityAttributeModifier
    > wrapConsumerForTooltip(
        BiConsumer<
            RegistryEntry<EntityAttribute>,
            EntityAttributeModifier
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
     * Processes a single modifier and applies conversion
     */
    @Unique
    private void processModifier(
        RegistryEntry<EntityAttribute> sourceAttr,
        EntityAttributeModifier modifier,
        BiConsumer<
            RegistryEntry<EntityAttribute>,
            EntityAttributeModifier
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

        RegistryEntry<EntityAttribute> targetAttr =
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
        RegistryEntry<EntityAttribute> sourceAttr,
        RegistryEntry<EntityAttribute> targetAttr,
        EntityAttributeModifier modifier,
        AttributeMappingEntry mapping,
        BiConsumer<
            RegistryEntry<EntityAttribute>,
            EntityAttributeModifier
        > consumer
    ) {
        if (mapping.isFullReplacement()) {
            AttributificationLogger.info(
                "[ItemStack] Hiding modifier for fullReplacement: " + sourceId
            );
            return;
        }

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

        EntityAttributeModifier convertedModifier = convertModifier(
            modifier,
            mapping
        );
        AttributificationLogger.info(
            "[ItemStack] Converted tooltip: " +
                sourceId +
                " -> " +
                AttributeUtils.getAttributeId(targetAttr) +
                ", value: " +
                modifier.value() +
                " -> " +
                convertedModifier.value()
        );
        consumer.accept(targetAttr, convertedModifier);
    }

    /**
     * Converts modifier for tooltip display
     */
    @Unique
    private EntityAttributeModifier convertModifier(
        EntityAttributeModifier modifier,
        AttributeMappingEntry mapping
    ) {
        EntityAttributeModifier.Operation op = modifier.operation();
        double value = modifier.value();

        // Convert value (only for ADD_VALUE)
        if (op == EntityAttributeModifier.Operation.ADD_VALUE) {
            value = value / mapping.getConversionEquivalence();
        }

        // Convert operation
        if (op == EntityAttributeModifier.Operation.ADD_VALUE) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    break;
                case DEFAULT:
                default:
                    break;
            }
        }

        return new EntityAttributeModifier(modifier.id(), value, op);
    }
}
