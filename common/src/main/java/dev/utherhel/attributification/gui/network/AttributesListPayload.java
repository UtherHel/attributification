package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record AttributesListPayload(List<String> attributes) {
    public static final Identifier ID = new Identifier(Attributifaction.MOD_ID, "attributes_list");

    public static AttributesListPayload decode(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<String> attributes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            attributes.add(buf.readString());
        }
        return new AttributesListPayload(attributes);
    }

    public void encode(PacketByteBuf buf) {
        buf.writeVarInt(attributes.size());
        for (String attr : attributes) {
            buf.writeString(attr);
        }
    }
}
