package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: requests conversion of legacy Fzzy Config mappings
 * to the new datapack format.
 */
//? if >=1.21 {
public record ConvertLegacyMappingsPayload() implements CustomPacketPayload {
//?} else {
/*public record ConvertLegacyMappingsPayload() {
*///?}

    //? if >=1.21 {
    public static final Type<ConvertLegacyMappingsPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "convert_legacy_mappings")
    );

    public static final StreamCodec<FriendlyByteBuf, ConvertLegacyMappingsPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), ConvertLegacyMappingsPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "convert_legacy_mappings");
    *///?}

    public static ConvertLegacyMappingsPayload decode(FriendlyByteBuf buf) {
        return new ConvertLegacyMappingsPayload();
    }

    public void encode(FriendlyByteBuf buf) {
    }
}
