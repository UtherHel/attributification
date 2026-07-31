package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record RequestAttributesListPayload() {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "request_attributes_list");

    public static RequestAttributesListPayload decode(PacketByteBuf buf) {
        return new RequestAttributesListPayload();
    }

    public void encode(PacketByteBuf buf) {
    }
}
