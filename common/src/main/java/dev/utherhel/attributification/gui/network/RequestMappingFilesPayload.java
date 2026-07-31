package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record RequestMappingFilesPayload() {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "request_mapping_files");

    public static RequestMappingFilesPayload decode(PacketByteBuf buf) {
        return new RequestMappingFilesPayload();
    }

    public void encode(PacketByteBuf buf) {
    }
}
