package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record DeleteMappingFilePayload(String fileName) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "delete_mapping_file");

    public static DeleteMappingFilePayload decode(PacketByteBuf buf) {
        return new DeleteMappingFilePayload(buf.readString());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeString(fileName);
    }
}
