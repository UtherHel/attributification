package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: notifies that legacy Fzzy Config mappings were found
 * and offers conversion.
 *
 * @param count the number of legacy mappings found (0 = none)
 */
//? if >=1.21 {
public record LegacyMappingsFoundPayload(int count) implements CustomPacketPayload {
//?} else {
/*public record LegacyMappingsFoundPayload(int count) {
*///?}

    //? if >=1.21 {
    public static final Type<LegacyMappingsFoundPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "legacy_mappings_found")
    );

    public static final StreamCodec<FriendlyByteBuf, LegacyMappingsFoundPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), LegacyMappingsFoundPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "legacy_mappings_found");
    *///?}

    public static LegacyMappingsFoundPayload decode(FriendlyByteBuf buf) {
        return new LegacyMappingsFoundPayload(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.count);
    }
}
