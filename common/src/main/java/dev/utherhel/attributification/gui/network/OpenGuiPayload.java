package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: signals the client to open the Attributification mappings GUI.
 */
public record OpenGuiPayload() implements CustomPayload {

    public static final Id<OpenGuiPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "open_gui")
    );

    public static final PacketCodec<PacketByteBuf, OpenGuiPayload> CODEC =
        PacketCodec.unit(new OpenGuiPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
