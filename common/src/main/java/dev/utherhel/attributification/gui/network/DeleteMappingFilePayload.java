package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: requests deletion of a mapping file.
 *
 * @param fileName the file name to delete
 */
public record DeleteMappingFilePayload(String fileName) implements CustomPayload {

    public static final Id<DeleteMappingFilePayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "delete_mapping_file")
    );

    public static final PacketCodec<PacketByteBuf, DeleteMappingFilePayload> CODEC =
        new PacketCodec<>() {
            @Override
            public DeleteMappingFilePayload decode(PacketByteBuf buf) {
                return new DeleteMappingFilePayload(buf.readString());
            }

            @Override
            public void encode(PacketByteBuf buf, DeleteMappingFilePayload payload) {
                buf.writeString(payload.fileName);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
