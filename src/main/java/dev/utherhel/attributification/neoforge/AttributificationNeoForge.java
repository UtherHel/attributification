package dev.utherhel.attributification.neoforge;

//? if neoforge {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.neoforge.config.AttributificationNeoForgeConfig;
import dev.utherhel.attributification.neoforge.gui.AttributificationPayloadHandler;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
/^import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
^///?} else {
import net.neoforged.neoforge.event.AddReloadListenerEvent;
//?}
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

@Mod(Attributifaction.MOD_ID)
public final class AttributificationNeoForge {

    public AttributificationNeoForge(
        IEventBus modEventBus,
        ModContainer container
    ) {
        // Create and register the NeoForge ModConfigSpec first
        var neoforgeConfig = AttributificationNeoForgeConfig.createAndRegister(
            container
        );

        // Run our common setup with the platform config
        Attributifaction.init(neoforgeConfig);

        // Register event handlers on the NeoForge event bus (not mod bus)
        NeoForge.EVENT_BUS.register(AttributificationNeoForgeEvents.class);
        NeoForge.EVENT_BUS.register(this);

        // Register GUI event handlers on the mod event bus
        modEventBus.register(AttributificationPayloadHandler.class);
    }

    /^*
     * Captures the current ResourceManager so the GUI payload handler
     * can resolve built-in datapack files and trigger reloads.
     ^/
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        AttributificationPayloadHandler.currentResourceManager = event
            .getPlayerList()
            .getServer()
            .getResourceManager();
    }

    /^*
     * Registers our datapack reload listener.
     * Called on server start and /reload.
     ^/
    //? if >=26.1 {
    /^@SubscribeEvent
    public void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
            ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mappings"),
            (ResourceManagerReloadListener) MappingLoader::load
        );
    }
    ^///?} else {
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) MappingLoader::load);
    }
    //?}
}
*///?}
