package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: requests the list of all registered attribute IDs
 * for the attribute suggestion dropdown in the GUI.
 */
public record RequestAttributesListPayload() implements CustomPacketPayload {

    public static final Type<RequestAttributesListPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_attributes_list")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestAttributesListPayload> CODEC =
        StreamCodec.unit(new RequestAttributesListPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
