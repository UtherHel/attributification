package dev.utherhel.attributification.forge;

//? if forge || (neoforge && <1.20.5) {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//? if forge {
/^import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
^///?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
//?}

import java.util.Collection;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Attributifaction.MOD_ID)
public final class AttributificationForgeEvents {
    private AttributificationForgeEvents() {}

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
        if (unifier == null) return;

        for (Map.Entry<Attribute, Collection<AttributeModifier>> entry : event.getModifiers().asMap().entrySet()) {
            Attribute sourceAttr = entry.getKey();
            String sourceId = AttributeUtils.getAttributeId(sourceAttr);
            if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

            AttributeMappingEntry mapping = unifier.getMapping(sourceId);
            if (mapping == null) continue;

            Attribute targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
            if (targetAttr == null) continue;

            Collection<AttributeModifier> modifiers = entry.getValue();

            if (mapping.isOnlyVisualChange()) {
                event.removeAttribute(sourceAttr);
                for (AttributeModifier modifier : modifiers) {
                    event.addModifier(targetAttr, modifier);
                }
                AttributificationLogger.info("[ItemAttributeModifierEvent] Moved " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr) + " (visual)");
                continue;
            }

            event.removeAttribute(sourceAttr);
            for (AttributeModifier modifier : modifiers) {
                AttributeModifier converted = convertModifier(modifier, mapping);
                event.addModifier(targetAttr, converted);
                AttributificationLogger.info("[ItemAttributeModifierEvent] Converted " + sourceId + " -> " +
                    AttributeUtils.getAttributeId(targetAttr) + " value: " + modifier.getAmount() + " -> " + converted.getAmount());
            }
        }
    }

    private static AttributeModifier convertModifier(AttributeModifier modifier, AttributeMappingEntry mapping) {
        AttributeModifier.Operation op = modifier.getOperation();
        double value = modifier.getAmount();

        if (op == AttributeModifier.Operation.ADDITION) {
            value = value / mapping.getConversionEquivalence();
        }

        if (op == AttributeModifier.Operation.ADDITION) {
            switch (mapping.getOperationConversionType()) {
                case ADDITION_TO_MULTIPLY_BASE:
                    op = AttributeModifier.Operation.MULTIPLY_BASE;
                    break;
                case ADDITION_TO_MULTIPLY_TOTAL:
                    op = AttributeModifier.Operation.MULTIPLY_TOTAL;
                    break;
                default:
                    break;
            }
        }

        //? if forge {
        /^return new AttributeModifier(modifier.getId(), modifier.getName(), value, op);
        ^///?} else {
        // 1.20.3+ has no getName(); the name is still saved with the modifier
        return new AttributeModifier(modifier.getId(), modifier.save().getString("Name"), value, op);
        //?}
    }
}
*///?}
