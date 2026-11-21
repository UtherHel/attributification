package dev.utherhel.attributification.gui;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

/** The {@code pack} object of {@code pack.mcmeta} for the world datapack the editor writes into. */
public final class DatapackMeta {

    private DatapackMeta() {}

    public static JsonObject worldPack(String description) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        //? if >=26.1 {
        /*// 1.21.9+ packs declare a supported range instead of a single pack_format
        int major = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
        pack.addProperty("min_format", major);
        pack.addProperty("max_format", major);
        *///?} else {
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        //?}
        return pack;
    }
}
