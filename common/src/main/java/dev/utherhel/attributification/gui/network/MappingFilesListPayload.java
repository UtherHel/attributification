package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: the list of available mapping files from datapacks.
 *
 * @param files list of mapping file entries
 */
public record MappingFilesListPayload(List<MappingFileEntry> files) implements CustomPayload {

    public static final Id<MappingFilesListPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "mapping_files_list")
    );

    public static final PacketCodec<PacketByteBuf, MappingFilesListPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public MappingFilesListPayload decode(PacketByteBuf buf) {
                int size = buf.readVarInt();
                List<MappingFileEntry> files = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    files.add(new MappingFileEntry(buf.readString(), buf.readBoolean()));
                }
                return new MappingFilesListPayload(files);
            }

            @Override
            public void encode(PacketByteBuf buf, MappingFilesListPayload payload) {
                buf.writeVarInt(payload.files.size());
                for (MappingFileEntry entry : payload.files) {
                    buf.writeString(entry.fileName());
                    buf.writeBoolean(entry.editable());
                }
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
