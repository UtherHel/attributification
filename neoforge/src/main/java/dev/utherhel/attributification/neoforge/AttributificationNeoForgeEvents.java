package dev.utherhel.attributification.neoforge;

import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
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

        for (AttributeModifiersComponent.Entry entry : modifiers) {
            RegistryEntry<EntityAttribute> attribute = entry.attribute();
            EntityAttributeModifier modifier = entry.modifier();
            AttributeModifierSlot slot = entry.slot();

            String sourceId = AttributeUtils.getAttributeId(attribute);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) continue;

            RegistryEntry<EntityAttribute> targetAttr =
                AttributeUtils.getFirstAvailableAttribute(
                    mapping.getTargetAttributes()
                );
            if (targetAttr == null) continue;

            // fullReplacement: remove source modifier entirely (source attribute frozen)
            if (mapping.isFullReplacement()) {
                conversions.add(new Conversion(attribute, modifier.id(), true));
                AttributificationLogger.info(
                    "[ItemAttributeModifierEvent] Full replacement, removing modifier: " +
                        sourceId
                );
                continue;
            }

            // onlyVisualChange: swap attribute reference, keep value
            if (mapping.isOnlyVisualChange()) {
                conversions.add(
                    new Conversion(
                        attribute,
                        modifier.id(),
                        false,
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
            EntityAttributeModifier convertedModifier = convertModifier(
                modifier,
                mapping
            );
            conversions.add(
                new Conversion(
                    attribute,
                    modifier.id(),
                    false,
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
                    modifier.value() +
                    " -> " +
                    convertedModifier.value()
            );
        }

        // Apply conversions
        for (Conversion conv : conversions) {
            event.removeModifier(conv.sourceAttribute(), conv.modifierId());
            if (!conv.removeOnly()) {
                event.addModifier(
                    conv.targetAttribute(),
                    conv.convertedModifier(),
                    conv.slot()
                );
            }
        }
    }

    /**
     * Converts a modifier value and operation according to mapping settings.
     */
    private static EntityAttributeModifier convertModifier(
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

    /**
     * Internal record for queued modifier conversions.
     */
    private record Conversion(
        RegistryEntry<EntityAttribute> sourceAttribute,
        Identifier modifierId,
        boolean removeOnly,
        RegistryEntry<EntityAttribute> targetAttribute,
        EntityAttributeModifier convertedModifier,
        AttributeModifierSlot slot
    ) {
        Conversion(
            RegistryEntry<EntityAttribute> source,
            Identifier modifierId,
            boolean removeOnly
        ) {
            this(source, modifierId, removeOnly, null, null, null);
        }
    }
}
