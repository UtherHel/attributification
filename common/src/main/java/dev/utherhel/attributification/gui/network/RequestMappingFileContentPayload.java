package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: requests the JSON content of a specific mapping file.
 *
 * @param fileName the file name to request
 */
public record RequestMappingFileContentPayload(String fileName) implements CustomPacketPayload {

    public static final Type<RequestMappingFileContentPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_mapping_file_content")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestMappingFileContentPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public RequestMappingFileContentPayload decode(FriendlyByteBuf buf) {
                return new RequestMappingFileContentPayload(buf.readUtf());
            }

            @Override
            public void encode(FriendlyByteBuf buf, RequestMappingFileContentPayload payload) {
                buf.writeUtf(payload.fileName);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
