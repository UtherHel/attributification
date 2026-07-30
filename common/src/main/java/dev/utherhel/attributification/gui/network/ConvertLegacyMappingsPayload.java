package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: requests conversion of legacy Fzzy Config mappings
 * to the new datapack format.
 */
public record ConvertLegacyMappingsPayload() implements CustomPayload {

    public static final Id<ConvertLegacyMappingsPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "convert_legacy_mappings")
    );

    public static final PacketCodec<PacketByteBuf, ConvertLegacyMappingsPayload> CODEC =
        PacketCodec.unit(new ConvertLegacyMappingsPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
