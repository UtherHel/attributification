package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: requests the list of available mapping files.
 */
//? if >=1.20.5 {
public record RequestMappingFilesPayload() implements CustomPacketPayload {
//?} else {
/*public record RequestMappingFilesPayload() {
*///?}

    //? if >=1.20.5 {
    public static final Type<RequestMappingFilesPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_mapping_files")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "request_mapping_files")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, RequestMappingFilesPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RequestMappingFilesPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "request_mapping_files");
    *///?}

    public static RequestMappingFilesPayload decode(FriendlyByteBuf buf) {
        return new RequestMappingFilesPayload();
    }

    public void encode(FriendlyByteBuf buf) {
    }
}
