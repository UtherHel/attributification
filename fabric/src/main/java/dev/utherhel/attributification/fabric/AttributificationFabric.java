package dev.utherhel.attributification.fabric;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.fabric.config.AttributificationFabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

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
            ResourceType.SERVER_DATA
        ).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Identifier.of(Attributifaction.MOD_ID, "mappings");
                }

                @Override
                public void reload(ResourceManager manager) {
                    MappingLoader.load(manager);
                    // Capture ResourceManager for GUI file operations
                    dev.utherhel.attributification.fabric.gui.AttributificationFabricPayloadHandler.setResourceManager(
                        manager
                    );
                }
            }
        );
    }
}
