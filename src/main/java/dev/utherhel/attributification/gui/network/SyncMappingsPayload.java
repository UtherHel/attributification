package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: the server's compiled attribute mappings, so the client computes attribute values
 * and tooltips the same way (mappings are data-pack files, which a client of a dedicated server does
 * not have). Sent when a player joins and after every reload of the mappings.
 *
 * @param token       identifies the server's load of the mappings; a client in the same game (single
 *                    player, the host of a LAN world) already has them and ignores the packet
 * @param jsonContent the mappings, see {@link dev.utherhel.attributification.attribute.MappingSync}
 */
//? if >=1.20.5 {
public record SyncMappingsPayload(String token, String jsonContent) implements CustomPacketPayload {
//?} else {
/*public record SyncMappingsPayload(String token, String jsonContent) {
*///?}

    //? if >=1.20.5 {
    public static final Type<SyncMappingsPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "sync_mappings")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "sync_mappings")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, SyncMappingsPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), SyncMappingsPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "sync_mappings");
    *///?}

    public static SyncMappingsPayload decode(FriendlyByteBuf buf) {
        return new SyncMappingsPayload(buf.readUtf(), JsonText.read(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.token);
        JsonText.write(buf, this.jsonContent);
    }
}
