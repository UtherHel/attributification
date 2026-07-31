package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record MappingFileContentPayload(String fileName, String jsonContent) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "mapping_file_content");

    public static MappingFileContentPayload decode(PacketByteBuf buf) {
        return new MappingFileContentPayload(buf.readString(), buf.readString());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeString(fileName);
        buf.writeString(jsonContent);
    }
}
