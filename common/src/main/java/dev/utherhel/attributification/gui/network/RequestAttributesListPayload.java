package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: requests the list of all registered attribute IDs
 * for the attribute suggestion dropdown in the GUI.
 */
public record RequestAttributesListPayload() implements CustomPayload {

    public static final Id<RequestAttributesListPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "request_attributes_list")
    );

    public static final PacketCodec<PacketByteBuf, RequestAttributesListPayload> CODEC =
        PacketCodec.unit(new RequestAttributesListPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
