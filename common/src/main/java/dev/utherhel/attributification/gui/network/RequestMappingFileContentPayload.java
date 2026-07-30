package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: requests the JSON content of a specific mapping file.
 *
 * @param fileName the file name to request
 */
public record RequestMappingFileContentPayload(String fileName) implements CustomPayload {

    public static final Id<RequestMappingFileContentPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "request_mapping_file_content")
    );

    public static final PacketCodec<PacketByteBuf, RequestMappingFileContentPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public RequestMappingFileContentPayload decode(PacketByteBuf buf) {
                return new RequestMappingFileContentPayload(buf.readString());
            }

            @Override
            public void encode(PacketByteBuf buf, RequestMappingFileContentPayload payload) {
                buf.writeString(payload.fileName);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
