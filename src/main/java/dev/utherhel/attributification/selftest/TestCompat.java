package dev.utherhel.attributification.selftest;

import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//? if >=1.20.5 {
import net.minecraft.core.Holder;
//?}
//? if >=1.21 {
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * The common calls of the self-test that differ between Minecraft versions (1.20.1 - 26.x, Stonecutter conditions;
 * the active version decides which branch is uncommented). Created by dev/modtest/sync.py when missing; owned by the
 * mod (scenes add their own helpers here).
 */
final class TestCompat {
    private TestCompat() {
    }

    static String versionName() {
//? if >=1.21.6 {
/*        return SharedConstants.getCurrentVersion().name();
*///?} else {
        return SharedConstants.getCurrentVersion().getName();
//?}
    }

    /** Runs a console command as the server (permission level 4, output suppressed). */
    static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Scoreboard tags of an entity. */
    static Set<String> tags(Entity e) {
//? if >=26.1 {
/*        return e.entityTags();
*///?} else {
        return e.getTags();
//?}
    }

    /** Vanilla attribute id as commands and datapacks spell it: "generic." / "player." prefixes were dropped in 1.21.2. */
    static String attributeId(String name) {
//? if >=1.21.2 {
/*        return "minecraft:" + name;
*///?} else {
        return "minecraft:" + (name.equals("block_interaction_range") || name.equals("entity_interaction_range")
            || name.equals("block_break_speed") ? "player." : "generic.") + name;
//?}
    }

    // ---------------------------------------------------------------- Attributification scenes

    /** The attribute behind an {@code Attributes} constant (a holder since 1.20.5). */
//? if >=1.20.5 {
    static Attribute attr(Holder<Attribute> a) {
        return a.value();
    }
//?} else {
/*    static Attribute attr(Attribute a) {
        return a;
    }
*///?}

    /** The registry id of an attribute, e.g. "minecraft:generic.luck" or (1.21.2+) "minecraft:luck". */
    static String id(Attribute a) {
        return String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(a));
    }

    /** A modifier with a stable id: ADD_VALUE, or ADD_MULTIPLIED_BASE when {@code multiplyBase}. */
    static AttributeModifier modifier(String name, double amount, boolean multiplyBase) {
//? if >=1.21 {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("modtest", name), amount,
            multiplyBase ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE);
//?} else if >=1.20.5 {
/*        return new AttributeModifier(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, amount,
            multiplyBase ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE);
*///?} else {
/*        return new AttributeModifier(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, amount,
            multiplyBase ? AttributeModifier.Operation.MULTIPLY_BASE : AttributeModifier.Operation.ADDITION);
*///?}
    }
}
