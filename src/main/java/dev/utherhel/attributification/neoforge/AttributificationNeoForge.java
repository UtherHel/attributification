package dev.utherhel.attributification.neoforge;

//? if neoforge && >=1.20.5 {
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
//? if >=1.21.4 {
/^import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
^///?} else {
import net.neoforged.neoforge.event.AddReloadListenerEvent;
//?}
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import dev.utherhel.attributification.attribute.MappingSync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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

        // After every reload of the mappings, every player gets the new ones
        MappingSync.broadcaster = payload -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            });
        };
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
        // a player joined: the server's mappings (a reload reaches everyone through MappingSync.broadcaster)
        ServerPlayer joined = event.getPlayer();
        if (joined != null) {
            PacketDistributor.sendToPlayer(joined, MappingSync.payload());
        }
    }

    /^*
     * Registers our datapack reload listener.
     * Called on server start and /reload.
     ^/
    //? if >=1.21.4 {
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
