package dev.utherhel.attributification.neoforge.mixin;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.AttributeUnifier;
import dev.utherhel.attributification.config.AttributeMappingEntry;
import dev.utherhel.attributification.util.AttributeUtils;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
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
            Consumer<Component> tooltip,
            Multimap<Holder<Attribute>, AttributeModifier> modifierMap,
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
            List<Map.Entry<Holder<Attribute>, AttributeModifier>> toRemove = new ArrayList<>();
            List<Map.Entry<Holder<Attribute>, AttributeModifier>> toAdd = new ArrayList<>();

            for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : modifierMap.entries()) {
                Holder<Attribute> attribute = entry.getKey();
                AttributeModifier modifier = entry.getValue();

                String sourceId = AttributeUtils.getAttributeId(attribute);
                if (sourceId == null || !unifier.shouldUnify(sourceId)) continue;

                AttributeMappingEntry mapping = unifier.getMapping(sourceId);
                if (mapping == null) continue;

                Holder<Attribute> targetAttr = AttributeUtils.getFirstAvailableAttribute(mapping.getTargetAttributes());
                if (targetAttr == null) continue;

                if (mapping.isOnlyVisualChange()) {
                    // Show with target name but original value
                    toRemove.add(entry);
                    toAdd.add(Map.entry(targetAttr, modifier));
                    AttributificationLogger.info("[NeoForgeTooltip] Visual change: " + sourceId + " -> " + AttributeUtils.getAttributeId(targetAttr));
                    continue;
                }

                // Convert modifier value and operation
                AttributeModifier convertedModifier = convertModifier(modifier, mapping);
                toRemove.add(entry);
                toAdd.add(Map.entry(targetAttr, convertedModifier));
                AttributificationLogger.info("[NeoForgeTooltip] Converted: " + sourceId + " -> " +
                    AttributeUtils.getAttributeId(targetAttr) + ", value: " + modifier.amount() + " -> " + convertedModifier.amount());
            }

            // Apply removals
            for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : toRemove) {
                modifierMap.remove(entry.getKey(), entry.getValue());
            }

            // Apply additions
            for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : toAdd) {
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
    private static AttributeModifier convertModifier(AttributeModifier modifier, AttributeMappingEntry mapping) {
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

        return new AttributeModifier(
            modifier.id(),
            value,
            op
        );
    }
}
