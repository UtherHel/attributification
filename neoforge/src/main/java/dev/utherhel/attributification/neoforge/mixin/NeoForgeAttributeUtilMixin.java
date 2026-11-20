package dev.utherhel.attributification.neoforge.mixin;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.common.util.AttributeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NeoForge-specific mixin that intercepts {@link AttributeUtil#applyTextFor}
 * to convert source → target attribute modifiers in tooltip rendering.
 * <p>
 * On NeoForge, tooltips are rendered via {@code AttributeUtil.applyTextFor()} and NOT
 * via the vanilla {@code ItemStack.applyAttributeModifier()} path in all cases.
 * Without this mixin, unified attributes would appear with the source name
 * and unconverted values in NeoForge tooltips.
 */
@Mixin(AttributeUtil.class)
public class NeoForgeAttributeUtilMixin {

    @Unique
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);

    /**
     * Wraps {@code applyTextFor} to convert source → target modifiers before tooltip rendering.
     */
    @WrapMethod(method = "applyTextFor")
    private static void wrapApplyTextFor(
            ItemStack stack,
            Consumer<Text> tooltip,
            Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifierMap,
            AttributeTooltipContext ctx,
            Operation<Void> original
    ) {
        if (Boolean.TRUE.equals(isProcessing.get())) {
            original.call(stack, tooltip, modifierMap, ctx);
            return;
        }

        isProcessing.set(true);
        try {
            AttributeUnifier unifier = Attributifaction.getAttributeUnifier();
            if (unifier == null) {
                original.call(stack, tooltip, modifierMap, ctx);
                return;
            }

            // Collect conversions: original entries that need to be removed + new entries to add
            List<Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> toRemove = new ArrayList<>();
            List<Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> toAdd = new ArrayList<>();

            for (Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeModifier> entry : modifierMap.entries()) {
                RegistryEntry<EntityAttribute> attribute = entry.getKey();
                EntityAttributeModifier modifier = entry.getValue();

                String sourceId = AttributeUtils.getAttributeId(attribute);
                if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

                AttributeMappingEntry mapping = unifier.getMapping(sourceId);
                if (mapping == null) continue;

                RegistryEntry<EntityAttribute> targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
                if (targetAttr == null) continue;

                if (mapping.isOnlyVisualChange()) {
                    // Show with target name but original value
                    toRemove.add(entry);
                    toAdd.add(Map.entry(targetAttr, modifier));
                    AttributificationLogger.info("[NeoForgeTooltip] Visual change: " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr));
                    continue;
                }

                // Convert modifier value and operation
                EntityAttributeModifier convertedModifier = convertModifier(modifier, mapping);
                toRemove.add(entry);
                toAdd.add(Map.entry(targetAttr, convertedModifier));
                AttributificationLogger.info("[NeoForgeTooltip] Converted: " + sourceId + " -> " +
                    AttributeUtils.getAttributeId(targetAttr) + ", value: " + modifier.value() + " -> " + convertedModifier.value());
            }

            // Apply removals
            for (Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeModifier> entry : toRemove) {
                modifierMap.remove(entry.getKey(), entry.getValue());
            }

            // Apply additions
            for (Map.Entry<RegistryEntry<EntityAttribute>, EntityAttributeModifier> entry : toAdd) {
                modifierMap.put(entry.getKey(), entry.getValue());
            }

            // Call original with the modified map
            original.call(stack, tooltip, modifierMap, ctx);
        } finally {
            isProcessing.remove();
        }
    }

    /**
     * Converts a modifier value and operation according to mapping settings.
     */
    @Unique
    private static EntityAttributeModifier convertModifier(EntityAttributeModifier modifier, AttributeMappingEntry mapping) {
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

        return new EntityAttributeModifier(
            modifier.id(),
            value,
            op
        );
    }
}
