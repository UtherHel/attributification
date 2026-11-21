package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: the JSON content of a requested mapping file.
 *
 * @param fileName    the file name
 * @param jsonContent the raw JSON content
 */
public record MappingFileContentPayload(String fileName, String jsonContent) implements CustomPacketPayload {

    public static final Type<MappingFileContentPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mapping_file_content")
    );

    public static final StreamCodec<FriendlyByteBuf, MappingFileContentPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public MappingFileContentPayload decode(FriendlyByteBuf buf) {
                return new MappingFileContentPayload(buf.readUtf(), buf.readUtf());
            }

            @Override
            public void encode(FriendlyByteBuf buf, MappingFileContentPayload payload) {
                buf.writeUtf(payload.fileName);
                buf.writeUtf(payload.jsonContent);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
