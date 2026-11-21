package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: signals the client to open the Attributification mappings GUI.
 */
public record OpenGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenGuiPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "open_gui")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenGuiPayload> CODEC =
        StreamCodec.unit(new OpenGuiPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
