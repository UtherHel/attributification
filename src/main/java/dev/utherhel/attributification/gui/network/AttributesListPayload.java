package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: the list of all registered attribute IDs.
 * Used by the GUI for attribute suggestion dropdown.
 *
 * @param attributes list of attribute string IDs (e.g., "minecraft:generic.attack_damage")
 */
//? if >=1.21 {
public record AttributesListPayload(List<String> attributes) implements CustomPacketPayload {
//?} else {
/*public record AttributesListPayload(List<String> attributes) {
*///?}

    //? if >=1.21 {
    public static final Type<AttributesListPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "attributes_list")
    );

    public static final StreamCodec<FriendlyByteBuf, AttributesListPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), AttributesListPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "attributes_list");
    *///?}

    public static AttributesListPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> attrs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            attrs.add(buf.readUtf());
        }
        return new AttributesListPayload(attrs);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.attributes.size());
        for (String attr : this.attributes) {
            buf.writeUtf(attr);
        }
    }
}
