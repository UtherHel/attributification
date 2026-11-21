package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: sends an edited mapping file to be saved.
 *
 * @param fileName    the file name to save as
 * @param jsonContent the entire JSON content to write
 */
//? if >=1.20.5 {
public record SaveMappingFilePayload(String fileName, String jsonContent) implements CustomPacketPayload {
//?} else {
/*public record SaveMappingFilePayload(String fileName, String jsonContent) {
*///?}

    //? if >=1.20.5 {
    public static final Type<SaveMappingFilePayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "save_mapping_file")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "save_mapping_file")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, SaveMappingFilePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), SaveMappingFilePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "save_mapping_file");
    *///?}

    public static SaveMappingFilePayload decode(FriendlyByteBuf buf) {
        return new SaveMappingFilePayload(buf.readUtf(), buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.fileName);
        buf.writeUtf(this.jsonContent);
    }
}
