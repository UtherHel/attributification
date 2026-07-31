package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record LegacyMappingsFoundPayload(int count) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "legacy_mappings_found");

    public static LegacyMappingsFoundPayload decode(PacketByteBuf buf) {
        return new LegacyMappingsFoundPayload(buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeVarInt(count);
    }
}
