package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: the JSON content of a requested mapping file.
 *
 * @param fileName    the file name
 * @param jsonContent the raw JSON content
 */
public record MappingFileContentPayload(String fileName, String jsonContent) implements CustomPayload {

    public static final Id<MappingFileContentPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "mapping_file_content")
    );

    public static final PacketCodec<PacketByteBuf, MappingFileContentPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public MappingFileContentPayload decode(PacketByteBuf buf) {
                return new MappingFileContentPayload(buf.readString(), buf.readString());
            }

            @Override
            public void encode(PacketByteBuf buf, MappingFileContentPayload payload) {
                buf.writeString(payload.fileName);
                buf.writeString(payload.jsonContent);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
