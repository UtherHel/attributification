package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: the list of all registered attribute IDs.
 * Used by the GUI for attribute suggestion dropdown.
 *
 * @param attributes list of attribute string IDs (e.g., "minecraft:generic.attack_damage")
 */
public record AttributesListPayload(List<String> attributes) implements CustomPayload {

    public static final Id<AttributesListPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "attributes_list")
    );

    public static final PacketCodec<PacketByteBuf, AttributesListPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public AttributesListPayload decode(PacketByteBuf buf) {
                int size = buf.readVarInt();
                List<String> attrs = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    attrs.add(buf.readString());
                }
                return new AttributesListPayload(attrs);
            }

            @Override
            public void encode(PacketByteBuf buf, AttributesListPayload payload) {
                buf.writeVarInt(payload.attributes.size());
                for (String attr : payload.attributes) {
                    buf.writeString(attr);
                }
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
