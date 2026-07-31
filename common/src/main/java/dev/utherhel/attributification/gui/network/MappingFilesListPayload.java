package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record MappingFilesListPayload(List<MappingFileEntry> files) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "mapping_files_list");

    public static MappingFilesListPayload decode(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<MappingFileEntry> files = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            files.add(new MappingFileEntry(buf.readString(), buf.readBoolean()));
        }
        return new MappingFilesListPayload(files);
    }

    public void encode(PacketByteBuf buf) {
        buf.writeVarInt(files.size());
        for (MappingFileEntry entry : files) {
            buf.writeString(entry.fileName());
            buf.writeBoolean(entry.editable());
        }
    }
}
