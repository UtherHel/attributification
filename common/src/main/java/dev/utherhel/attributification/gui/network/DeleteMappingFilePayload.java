package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: requests deletion of a mapping file.
 *
 * @param fileName the file name to delete
 */
public record DeleteMappingFilePayload(String fileName) implements CustomPacketPayload {

    public static final Type<DeleteMappingFilePayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "delete_mapping_file")
    );

    public static final StreamCodec<FriendlyByteBuf, DeleteMappingFilePayload> CODEC =
        new StreamCodec<>() {
            @Override
            public DeleteMappingFilePayload decode(FriendlyByteBuf buf) {
                return new DeleteMappingFilePayload(buf.readUtf());
            }

            @Override
            public void encode(FriendlyByteBuf buf, DeleteMappingFilePayload payload) {
                buf.writeUtf(payload.fileName);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
