package dev.utherhel.attributification.selftest;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributeUnifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Server scenes of Attributification, on the mappings of the test datapack (dev/modtest.toml, files_server): one
 * mapping per mode, run against a real zombie's attribute map, plus a cycle that must be dropped and a /reload.
 *
 * <pre>
 * luck                 -> armor_toughness  unidirectional
 * attack_knockback     -> follow_range     bidirectional, equivalence 0.5, subtract 10, conversionEquivalence 4
 * spawn_reinforcements -> movement_speed   bidirectional, ADDITION_TO_MULTIPLY_BASE
 * armor                -> max_health       fullReplacement
 * knockback_resistance -> max_health       onlyVisualChange
 * attack_damage        <-> attack_speed    a cycle: both dropped
 * </pre>
 */
final class Scenarios {
    private static final String ZOMBIE = "mt_attr_zombie";

    private Scenarios() {
    }

    static void server(Script<MinecraftServer> s) {
        if (s.scene("mappings")) {
            s.check("datapack_loaded", srv -> {
                AttributeUnifier u = Attributifaction.getAttributeUnifier();
                if (u == null) return "no attribute unifier";
                return u.shouldUnify(TestCompat.id(TestCompat.attr(Attributes.LUCK))) ? null
                    : "the test mapping for " + TestCompat.id(TestCompat.attr(Attributes.LUCK)) + " is not loaded";
            }).check("cycle_dropped", srv -> {
                AttributeUnifier u = Attributifaction.getAttributeUnifier();
                String dmg = TestCompat.id(TestCompat.attr(Attributes.ATTACK_DAMAGE));
                String spd = TestCompat.id(TestCompat.attr(Attributes.ATTACK_SPEED));
                return !u.shouldUnify(dmg) && !u.shouldUnify(spd) ? null
                    : "attack_damage <-> attack_speed still unified: " + dmg + "=" + u.shouldUnify(dmg) + ", " + spd + "="
                    + u.shouldUnify(spd);
            });
        }
        if (s.scene("modes")) {
            s.run(srv -> {
                Probe.cmd(srv, "kill @e[type=!minecraft:player]",
                    "summon minecraft:zombie 0 -60 4 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"" + ZOMBIE + "\"]}");
                LivingEntity z = Probe.living(srv, ZOMBIE);
                z.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(7);
                z.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(30);
                z.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
                z.getAttribute(Attributes.ARMOR).setBaseValue(2);
                z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
            })
                .check("unidirectional_value", srv ->
                    near("luck (reads armor_toughness)", zombie(srv).getAttributeValue(Attributes.LUCK), 7))
                .check("unidirectional_keeps_instance", srv ->
                    zombie(srv).getAttribute(Attributes.LUCK) != zombie(srv).getAttribute(Attributes.ARMOR_TOUGHNESS) ? null
                        : "a unidirectional source lookup returned the target's attribute instance")
                .check("bidirectional_value", srv ->
                    near("attack_knockback = (follow_range 30 - 10) x 0.5", zombie(srv).getAttributeValue(Attributes.ATTACK_KNOCKBACK), 10))
                .check("bidirectional_shares_instance", srv ->
                    zombie(srv).getAttribute(Attributes.ATTACK_KNOCKBACK) == zombie(srv).getAttribute(Attributes.FOLLOW_RANGE) ? null
                        : "attack_knockback lookups do not return the follow_range instance")
                .check("modifier_converted", srv -> {
                    zombie(srv).getAttribute(Attributes.ATTACK_KNOCKBACK).addTransientModifier(TestCompat.modifier("kb", 8, false));
                    String r = near("follow_range after +8 attack_knockback (/4)", zombie(srv).getAttributeValue(Attributes.FOLLOW_RANGE), 32);
                    return r != null ? r : near("attack_knockback after the modifier", zombie(srv).getAttributeValue(Attributes.ATTACK_KNOCKBACK), 11);
                })
                .check("operation_converted", srv -> {
                    zombie(srv).getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addTransientModifier(TestCompat.modifier("sr", 0.5, false));
                    return near("movement_speed after +0.5 spawn_reinforcements as multiply-base", zombie(srv).getAttributeValue(Attributes.MOVEMENT_SPEED), 0.3);
                })
                .check("target_modifier_not_converted", srv -> {
                    zombie(srv).getAttribute(Attributes.FOLLOW_RANGE).addTransientModifier(TestCompat.modifier("fr", 4, false));
                    return near("follow_range after +4 on itself", zombie(srv).getAttributeValue(Attributes.FOLLOW_RANGE), 36);
                })
                .check("full_replacement_frozen", srv -> {
                    zombie(srv).getAttribute(Attributes.ARMOR).addTransientModifier(TestCompat.modifier("ar", 5, false));
                    return near("armor (frozen at its base 2, +5 modifier ignored)", zombie(srv).getAttributeValue(Attributes.ARMOR), 2);
                })
                .check("visual_only_value", srv ->
                    near("knockback_resistance (onlyVisualChange keeps its value)", zombie(srv).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.3))
                .check("visual_only_name", srv -> sameName("knockback_resistance", "max_health",
                    TestCompat.attr(Attributes.KNOCKBACK_RESISTANCE).getDescriptionId(), TestCompat.attr(Attributes.MAX_HEALTH).getDescriptionId()))
                .check("source_named_after_target", srv -> sameName("luck", "armor_toughness",
                    TestCompat.attr(Attributes.LUCK).getDescriptionId(), TestCompat.attr(Attributes.ARMOR_TOUGHNESS).getDescriptionId()))
                .check("unmapped_untouched", srv ->
                    near("attack_damage (cycle dropped: vanilla 3)", zombie(srv).getAttributeValue(Attributes.ATTACK_DAMAGE), 3));
        }
        if (s.scene("reload")) {
            s.run(srv -> Probe.cmd(srv, "reload"))
                .waitTicks(20)
                .eventually("mappings_survive_reload", 20 * 30, srv -> {
                    AttributeUnifier u = Attributifaction.getAttributeUnifier();
                    if (u == null || !u.shouldUnify(TestCompat.id(TestCompat.attr(Attributes.LUCK)))) return "no luck mapping after /reload";
                    return near("luck after /reload", zombie(srv).getAttributeValue(Attributes.LUCK), 7);
                })
                .run(srv -> Probe.cmd(srv, "kill @e[type=!minecraft:player]"));
        }
    }

    static void player(Script<MinecraftServer> s) {
        if (s.scene("player")) {
            s.run(srv -> {
                ServerPlayer p = SelfTest.player(srv);
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            }).waitTicks(10)
                .check("player_luck_reads_toughness", srv ->
                    near("player luck with a diamond chestplate (toughness 2)", SelfTest.player(srv).getAttributeValue(Attributes.LUCK), 2));
        }
    }

    // ---------------------------------------------------------------- helpers
    static LivingEntity zombie(MinecraftServer srv) {
        return Probe.living(srv, ZOMBIE);
    }

    static String near(String what, double actual, double expected) {
        return Probe.near(what, actual, expected, 1e-6);
    }

    static String sameName(String source, String target, String sourceKey, String targetKey) {
        return sourceKey.equals(targetKey) ? null : source + " is named " + sourceKey + ", expected the " + target + " name " + targetKey;
    }
}
