package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: sends an edited mapping file to be saved.
 *
 * @param fileName    the file name to save as
 * @param jsonContent the entire JSON content to write
 */
public record SaveMappingFilePayload(String fileName, String jsonContent) implements CustomPayload {

    public static final Id<SaveMappingFilePayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "save_mapping_file")
    );

    public static final PacketCodec<PacketByteBuf, SaveMappingFilePayload> CODEC =
        new PacketCodec<>() {
            @Override
            public SaveMappingFilePayload decode(PacketByteBuf buf) {
                return new SaveMappingFilePayload(buf.readString(), buf.readString());
            }

            @Override
            public void encode(PacketByteBuf buf, SaveMappingFilePayload payload) {
                buf.writeString(payload.fileName);
                buf.writeString(payload.jsonContent);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
