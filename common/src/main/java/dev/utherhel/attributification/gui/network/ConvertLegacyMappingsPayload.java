package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: requests conversion of legacy Fzzy Config mappings
 * to the new datapack format.
 */
public record ConvertLegacyMappingsPayload() implements CustomPacketPayload {

    public static final Type<ConvertLegacyMappingsPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "convert_legacy_mappings")
    );

    public static final StreamCodec<FriendlyByteBuf, ConvertLegacyMappingsPayload> CODEC =
        StreamCodec.unit(new ConvertLegacyMappingsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
