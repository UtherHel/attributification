package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Server → Client: signals the client to open the Attributification mappings GUI.
 */
//? if >=1.20.5 {
public record OpenGuiPayload() implements CustomPacketPayload {
//?} else {
/*public record OpenGuiPayload() {
*///?}

    //? if >=1.20.5 {
    public static final Type<OpenGuiPayload> ID = new Type<>(
        //? if >=1.21 {
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "open_gui")
        //?} else {
        /*new ResourceLocation(Attributifaction.MOD_ID, "open_gui")
        *///?}
    );

    public static final StreamCodec<FriendlyByteBuf, OpenGuiPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), OpenGuiPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Attributifaction.MOD_ID, "open_gui");
    *///?}

    public static OpenGuiPayload decode(FriendlyByteBuf buf) {
        return new OpenGuiPayload();
    }

    public void encode(FriendlyByteBuf buf) {
    }
}
