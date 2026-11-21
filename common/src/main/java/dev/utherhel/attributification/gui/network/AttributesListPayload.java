package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: the list of all registered attribute IDs.
 * Used by the GUI for attribute suggestion dropdown.
 *
 * @param attributes list of attribute string IDs (e.g., "minecraft:generic.attack_damage")
 */
public record AttributesListPayload(List<String> attributes) implements CustomPacketPayload {

    public static final Type<AttributesListPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "attributes_list")
    );

    public static final StreamCodec<FriendlyByteBuf, AttributesListPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public AttributesListPayload decode(FriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<String> attrs = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    attrs.add(buf.readUtf());
                }
                return new AttributesListPayload(attrs);
            }

            @Override
            public void encode(FriendlyByteBuf buf, AttributesListPayload payload) {
                buf.writeVarInt(payload.attributes.size());
                for (String attr : payload.attributes) {
                    buf.writeUtf(attr);
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
