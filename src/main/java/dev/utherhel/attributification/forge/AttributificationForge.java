package dev.utherhel.attributification.forge;

//? if forge {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.forge.config.AttributificationForgeConfig;
import dev.utherhel.attributification.forge.gui.AttributificationPayloadHandler;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Attributifaction.MOD_ID)
public final class AttributificationForge {
    public AttributificationForge() {
        // Register the Forge config first; values are read lazily once Forge has loaded the file
        AttributificationForgeConfig forgeConfig = AttributificationForgeConfig.createAndRegister();

        // Run our common setup with the platform config
        Attributifaction.init(forgeConfig);

        // Datapack reload listener for attribute mappings (data/<namespace>/attributification/mappings/*.json)
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
            event.addListener((ResourceManagerReloadListener) MappingLoader::load));

        AttributificationPayloadHandler.register();
    }
}
*///?}
