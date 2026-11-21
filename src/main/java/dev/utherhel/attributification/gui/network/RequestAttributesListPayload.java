package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client → Server: requests the list of all registered attribute IDs
 * for the attribute suggestion dropdown in the GUI.
 */
//? if >=1.20.5 {
public record RequestAttributesListPayload() implements CustomPacketPayload {
//?} else {
/*public record RequestAttributesListPayload() {
*///?}

    //? if >=1.20.5 {
    public static final Type<RequestAttributesListPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "request_attributes_list")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "request_attributes_list")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, RequestAttributesListPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RequestAttributesListPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "request_attributes_list");
    *///?}

    public static RequestAttributesListPayload decode(FriendlyByteBuf buf) {
        return new RequestAttributesListPayload();
    }

    public void encode(FriendlyByteBuf buf) {
    }
}
