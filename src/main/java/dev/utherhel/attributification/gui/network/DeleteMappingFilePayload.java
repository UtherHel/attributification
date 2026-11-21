package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: requests deletion of a mapping file.
 *
 * @param fileName the file name to delete
 */
//? if >=1.21 {
public record DeleteMappingFilePayload(String fileName) implements CustomPacketPayload {
//?} else {
/*public record DeleteMappingFilePayload(String fileName) {
*///?}

    //? if >=1.21 {
    public static final Type<DeleteMappingFilePayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "delete_mapping_file")
    );

    public static final StreamCodec<FriendlyByteBuf, DeleteMappingFilePayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), DeleteMappingFilePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "delete_mapping_file");
    *///?}

    public static DeleteMappingFilePayload decode(FriendlyByteBuf buf) {
        return new DeleteMappingFilePayload(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.fileName);
    }
}
