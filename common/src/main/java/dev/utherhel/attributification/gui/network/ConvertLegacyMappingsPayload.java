package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record ConvertLegacyMappingsPayload() {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "convert_legacy_mappings");

    public static ConvertLegacyMappingsPayload decode(PacketByteBuf buf) {
        return new ConvertLegacyMappingsPayload();
    }

    public void encode(PacketByteBuf buf) {
    }
}
