package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: requests the list of available mapping files.
 */
public record RequestMappingFilesPayload() implements CustomPayload {

    public static final Id<RequestMappingFilesPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "request_mapping_files")
    );

    public static final PacketCodec<PacketByteBuf, RequestMappingFilesPayload> CODEC =
        PacketCodec.unit(new RequestMappingFilesPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
