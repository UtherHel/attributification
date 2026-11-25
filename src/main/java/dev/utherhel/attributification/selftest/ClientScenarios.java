package dev.utherhel.attributification.selftest;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributeUnifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/**
 * Client scenes of the self-test (client-only class). {@link #title} runs from the title screen without a world
 * (dev/mt.py client), {@link #client} in the world of a dedicated server (dev/mt.py mp).
 *
 * <p>On a dedicated server the mappings come from the server's datapacks. The client scenes check what a player's
 * client makes of them: the player's attribute values (computed client-side from the synced attributes) and the
 * names shown for mapped attributes.
 */
final class ClientScenarios {
    private ClientScenarios() {
    }

    static void title(Script<Minecraft> s) {
        if (s.scene("title")) {
            s.check("unifier_ready", mc -> Attributifaction.getAttributeUnifier() != null ? null
                : "no attribute unifier on the client");
        }
    }

    static void client(Script<Minecraft> s) {
        if (s.scene("client")) {
            s.check("player_present", mc -> mc.player != null && mc.player.isAlive() ? null : "no living local player");
            if (!ClientSelfTest.serverMod()) {
                // a server without the mod: no mappings anywhere, vanilla values
                s.check("vanilla_server_luck", mc -> Probe.near("luck", mc.player.getAttributeValue(Attributes.LUCK), 0, 1e-6));
                return;
            }
            // the server's player scene puts a diamond chestplate (armor toughness 2) on the player
            s.until("chestplate from the server", 20 * 60, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE))
                .waitTicks(10)
                .check("client_has_server_mappings", mc -> {
                    AttributeUnifier u = Attributifaction.getAttributeUnifier();
                    String luck = TestCompat.id(TestCompat.attr(Attributes.LUCK));
                    return u != null && u.shouldUnify(luck) ? null
                        : "the client knows no mapping for " + luck + " (the server's datapack mappings are not synced to "
                        + "clients: client-side values and tooltips ignore them)";
                })
                .check("client_luck_matches_server", mc ->
                    Probe.near("client-side luck (the server computes 2 from armor_toughness)", mc.player.getAttributeValue(Attributes.LUCK), 2, 1e-6));
        }
    }
}
