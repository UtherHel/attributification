package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: requests the JSON content of a specific mapping file.
 *
 * @param fileName the file name to request
 */
//? if >=1.20.5 {
public record RequestMappingFileContentPayload(String fileName) implements CustomPacketPayload {
//?} else {
/*public record RequestMappingFileContentPayload(String fileName) {
*///?}

    //? if >=1.20.5 {
    public static final Type<RequestMappingFileContentPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_mapping_file_content")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "request_mapping_file_content")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, RequestMappingFileContentPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RequestMappingFileContentPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "request_mapping_file_content");
    *///?}

    public static RequestMappingFileContentPayload decode(FriendlyByteBuf buf) {
        return new RequestMappingFileContentPayload(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.fileName);
    }
}
