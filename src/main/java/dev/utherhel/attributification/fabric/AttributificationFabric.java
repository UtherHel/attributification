package dev.utherhel.attributification.fabric;

//? if fabric {
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.fabric.config.AttributificationFabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class AttributificationFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Create and load the Fabric JSON config
        AttributificationFabricConfig fabricConfig =
            new AttributificationFabricConfig();
        fabricConfig.load();

        // Run our common setup with the platform config
        Attributifaction.init(fabricConfig);

        // Register GUI networking and command
        dev.utherhel.attributification.fabric.gui.AttributificationFabricPayloadHandler.register();
        dev.utherhel.attributification.fabric.gui.AttributificationFabricCommand.register();

        // Register datapack reload listener for attribute mappings
        // This reads data/<namespace>/attributification/mappings/*.json
        ResourceManagerHelper.get(
            PackType.SERVER_DATA
        ).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    //? if >=1.21 {
                    return ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "mappings");
                    //?} else {
                    /*return new ResourceLocation(Attributifaction.MOD_ID, "mappings");
                    *///?}
                }

                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    MappingLoader.load(manager);
                    //? if >=1.21 {
                    // Capture ResourceManager for GUI file operations
                    dev.utherhel.attributification.fabric.gui.AttributificationFabricPayloadHandler.setResourceManager(
                        manager
                    );
                    //?}
                }
            }
        );
    }
}
//?}
