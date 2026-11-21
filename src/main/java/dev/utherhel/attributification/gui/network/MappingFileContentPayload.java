package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: the JSON content of a requested mapping file.
 *
 * @param fileName    the file name
 * @param jsonContent the raw JSON content
 */
//? if >=1.20.5 {
public record MappingFileContentPayload(String fileName, String jsonContent) implements CustomPacketPayload {
//?} else {
/*public record MappingFileContentPayload(String fileName, String jsonContent) {
*///?}

    //? if >=1.20.5 {
    public static final Type<MappingFileContentPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mapping_file_content")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "mapping_file_content")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, MappingFileContentPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), MappingFileContentPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "mapping_file_content");
    *///?}

    public static MappingFileContentPayload decode(FriendlyByteBuf buf) {
        return new MappingFileContentPayload(buf.readUtf(), buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.fileName);
        buf.writeUtf(this.jsonContent);
    }
}
