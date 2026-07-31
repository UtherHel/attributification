package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record RequestMappingFileContentPayload(String fileName) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "request_mapping_file_content");

    public static RequestMappingFileContentPayload decode(PacketByteBuf buf) {
        return new RequestMappingFileContentPayload(buf.readString());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeString(fileName);
    }
}
