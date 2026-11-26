package dev.utherhel.attributification.forge;

//? if forge || (neoforge && <1.20.5) {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.forge.config.AttributificationForgeConfig;
import dev.utherhel.attributification.forge.gui.AttributificationPayloadHandler;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
//? if forge {
/^import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;
^///?} else {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
//?}

@Mod(Attributifaction.MOD_ID)
public final class AttributificationForge {
    //? if forge {
    /^public AttributificationForge() {
    ^///?} else {
    public AttributificationForge(IEventBus modBus) {
    //?}
        // Register the Forge config first; values are read lazily once Forge has loaded the file
        AttributificationForgeConfig forgeConfig = AttributificationForgeConfig.createAndRegister();

        // Run our common setup with the platform config
        Attributifaction.init(forgeConfig);

        // Datapack reload listener for attribute mappings (data/<namespace>/attributification/mappings/*.json)
        //? if forge {
        /^MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
        ^///?} else {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
        //?}
            event.addListener((ResourceManagerReloadListener) MappingLoader::load));

        // The server's mappings to a player who joins (a reload reaches everyone through MappingSync.broadcaster)
        //? if forge {
        /^MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent event) ->
        ^///?} else {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent event) ->
        //?}
            AttributificationPayloadHandler.onDatapackSync(event.getPlayerList().getServer(), event.getPlayer()));

        //? if forge {
        /^AttributificationPayloadHandler.register();
        ^///?} else {
        AttributificationPayloadHandler.register();
        // NeoForge 20.4 registers payloads in an event on the mod bus
        modBus.addListener(AttributificationPayloadHandler.CHANNEL::register);
        //?}
    }
}
*///?}
