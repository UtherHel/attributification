package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record OpenGuiPayload() {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "open_gui");

    public static OpenGuiPayload decode(PacketByteBuf buf) {
        return new OpenGuiPayload();
    }

    public void encode(PacketByteBuf buf) {
    }
}
