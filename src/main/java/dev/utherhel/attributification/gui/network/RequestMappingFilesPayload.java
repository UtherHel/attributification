package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: requests the list of available mapping files.
 */
public record RequestMappingFilesPayload() implements CustomPacketPayload {

    public static final Type<RequestMappingFilesPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_mapping_files")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestMappingFilesPayload> CODEC =
        StreamCodec.unit(new RequestMappingFilesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
