package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: sends an edited mapping file to be saved.
 *
 * @param fileName    the file name to save as
 * @param jsonContent the entire JSON content to write
 */
public record SaveMappingFilePayload(String fileName, String jsonContent) implements CustomPacketPayload {

    public static final Type<SaveMappingFilePayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "save_mapping_file")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveMappingFilePayload> CODEC =
        new StreamCodec<>() {
            @Override
            public SaveMappingFilePayload decode(FriendlyByteBuf buf) {
                return new SaveMappingFilePayload(buf.readUtf(), buf.readUtf());
            }

            @Override
            public void encode(FriendlyByteBuf buf, SaveMappingFilePayload payload) {
                buf.writeUtf(payload.fileName);
                buf.writeUtf(payload.jsonContent);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
