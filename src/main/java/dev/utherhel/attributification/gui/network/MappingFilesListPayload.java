package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: the list of available mapping files from datapacks.
 *
 * @param files list of mapping file entries
 */
//? if >=1.21 {
public record MappingFilesListPayload(List<MappingFileEntry> files) implements CustomPacketPayload {
//?} else {
/*public record MappingFilesListPayload(List<MappingFileEntry> files) {
*///?}

    //? if >=1.21 {
    public static final Type<MappingFilesListPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mapping_files_list")
    );

    public static final StreamCodec<FriendlyByteBuf, MappingFilesListPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), MappingFilesListPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "mapping_files_list");
    *///?}

    public static MappingFilesListPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MappingFileEntry> files = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            files.add(new MappingFileEntry(buf.readUtf(), buf.readBoolean()));
        }
        return new MappingFilesListPayload(files);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.files.size());
        for (MappingFileEntry entry : this.files) {
            buf.writeUtf(entry.fileName());
            buf.writeBoolean(entry.editable());
        }
    }
}
