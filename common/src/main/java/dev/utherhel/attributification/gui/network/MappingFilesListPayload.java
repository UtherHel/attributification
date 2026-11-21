package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: the list of available mapping files from datapacks.
 *
 * @param files list of mapping file entries
 */
public record MappingFilesListPayload(List<MappingFileEntry> files) implements CustomPacketPayload {

    public static final Type<MappingFilesListPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mapping_files_list")
    );

    public static final StreamCodec<FriendlyByteBuf, MappingFilesListPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public MappingFilesListPayload decode(FriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<MappingFileEntry> files = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    files.add(new MappingFileEntry(buf.readUtf(), buf.readBoolean()));
                }
                return new MappingFilesListPayload(files);
            }

            @Override
            public void encode(FriendlyByteBuf buf, MappingFilesListPayload payload) {
                buf.writeVarInt(payload.files.size());
                for (MappingFileEntry entry : payload.files) {
                    buf.writeUtf(entry.fileName());
                    buf.writeBoolean(entry.editable());
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
