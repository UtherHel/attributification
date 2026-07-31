package dev.utherhel.attributification.forge;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Attributifaction.MOD_ID)
public final class AttributificationForgeEvents {
    private AttributificationForgeEvents() {}

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return;

        for (Map.Entry<EntityAttribute, Collection<EntityAttributeModifier>> entry : event.getModifiers().asMap().entrySet()) {
            EntityAttribute sourceAttr = entry.getKey();
            String sourceId = AttributeUtils.getAttributeId(sourceAttr);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) continue;

            EntityAttribute targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (targetAttr == null) continue;

            Collection<EntityAttributeModifier> modifiers = entry.getValue();

            if (mapping.isFullReplacement()) {
                event.removeAttribute(sourceAttr);
                AttributificationLogger.info("[ItemAttributeModifierEvent] Removed " + sourceId + " modifiers for fullReplacement");
                continue;
            }

            if (mapping.isOnlyVisualChange()) {
                event.removeAttribute(sourceAttr);
                for (EntityAttributeModifier modifier : modifiers) {
                    event.addModifier(targetAttr, modifier);
                }
                AttributificationLogger.info("[ItemAttributeModifierEvent] Moved " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr) + " (visual)");
                continue;
            }

            event.removeAttribute(sourceAttr);
            for (EntityAttributeModifier modifier : modifiers) {
                EntityAttributeModifier converted = convertModifier(modifier, mapping);
                event.addModifier(targetAttr, converted);
                AttributificationLogger.info("[ItemAttributeModifierEvent] Converted " + sourceId + " -> " +
                    AttributeUtils.getAttributeId(targetAttr) + " value: " + modifier.getValue() + " -> " + converted.getValue());
            }
        }
    }

    private static EntityAttributeModifier convertModifier(EntityAttributeModifier modifier, AttributeMappingEntry mapping) {
        EntityAttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getValue();

        if (op == EntityAttributeModifier.Operation.ADDITION) {
            value = value / mapping.getConversionEquivalence();
        }

        if (op == EntityAttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = EntityAttributeModifier.Operation.MULTIPLY_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = EntityAttributeModifier.Operation.MULTIPLY_TOTAL;
                    break;
                default:
                    break;
            }
        }

        return new EntityAttributeModifier(modifier.getId(), modifier.getName(), value, op);
    }
}
