package dev.utherhel.attributification.neoforge;

import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Handles NeoForge-specific events for attribute conversion.
 * <p>
 * On NeoForge, item attribute modifiers pass through {@link ItemAttributeModifierEvent}.
 * We intercept this to convert source → target modifiers at the gameplay level.
 */
public final class AttributificationNeoForgeEvents {

    private AttributificationNeoForgeEvents() {}

    /**
     * Called when item attribute modifiers are being computed.
     * Converts source → target modifiers to ensure gameplay unification.
     */
    @SubscribeEvent
    public static void onItemAttributeModifiers(
        ItemAttributeModifierEvent event
    ) {
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return;

        var modifiers = event.getModifiers();
        if (modifiers.isEmpty()) return;

        // Collect conversions to apply after iteration
        List<Conversion> conversions = new ArrayList<>();

        for (ItemAttributeModifiers.Entry entry : modifiers) {
            Holder<Attribute> attribute = entry.attribute();
            AttributeModifier modifier = entry.modifier();
            EquipmentSlotGroup slot = entry.slot();

            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) continue;

            Holder<Attribute> targetAttr =
                AttributeUtils.getFirstAvailableAttribute(
                    mapping.getTargetAttributes()
                );
            if (targetAttr == null) continue;

            // onlyVisualChange: swap attribute reference, keep value
            if (mapping.isOnlyVisualChange()) {
                conversions.add(
                    new Conversion(
                        attribute,
                        modifier.id(),
                        targetAttr,
                        modifier,
                        slot
                    )
                );
                AttributificationLogger.info(
                    "[ItemAttributeModifierEvent] Visual change: " +
                        sourceId +
                        " -> " +
                        AttributeUtils.getAttributeId(targetAttr)
                );
                continue;
            }

            // Convert modifier value and operation
            AttributeModifier convertedModifier = convertModifier(
                modifier,
                mapping
            );
            conversions.add(
                new Conversion(
                    attribute,
                    modifier.id(),
                    targetAttr,
                    convertedModifier,
                    slot
                )
            );
            AttributificationLogger.info(
                "[ItemAttributeModifierEvent] Converted: " +
                    sourceId +
                    " -> " +
                    AttributeUtils.getAttributeId(targetAttr) +
                    ", value: " +
                    modifier.amount() +
                    " -> " +
                    convertedModifier.amount()
            );
        }

        // Apply conversions
        for (Conversion conv : conversions) {
            event.removeModifier(conv.sourceAttribute(), conv.modifierId());
            event.addModifier(
                conv.targetAttribute(),
                conv.convertedModifier(),
                conv.slot()
            );
        }
    }

    /**
     * Converts a modifier value and operation according to mapping settings.
     */
    private static AttributeModifier convertModifier(
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

    /**
     * Internal record for queued modifier conversions.
     */
    private record Conversion(
        Holder<Attribute> sourceAttribute,
        ResourceLocation modifierId,
        Holder<Attribute> targetAttribute,
        AttributeModifier convertedModifier,
        EquipmentSlotGroup slot
    ) {}
}
