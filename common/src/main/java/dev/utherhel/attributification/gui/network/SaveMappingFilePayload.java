package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SaveMappingFilePayload(String fileName, String jsonContent) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "save_mapping_file");

    public static SaveMappingFilePayload decode(PacketByteBuf buf) {
        return new SaveMappingFilePayload(buf.readString(), buf.readString());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeString(fileName);
        buf.writeString(jsonContent);
    }
}
