package dev.utherhel.attributification.forge;

import dev.architectury.platform.forge.EventBuses;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.forge.config.AttributificationForgeConfig;
import dev.utherhel.attributification.forge.gui.AttributificationPayloadHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Attributifaction.MOD_ID)
public final class AttributificationForge {
    public AttributificationForge() {
        EventBuses.registerModEventBus(Attributifaction.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AttributificationForgeConfig.getSpec());

        // Do NOT read config values here: ModConfigSpec throws
        // "Cannot get config value before config is loaded" until the
        // config loading event fires. Apply the level once it is loaded.
        Attributifaction.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfigEvent event) -> {
            if (event.getConfig().getSpec() == AttributificationForgeConfig.getSpec()) {
                Attributifaction.getConfig().setLogLevel(AttributificationForgeConfig.getLogLevel());
            }
        });

        AttributificationPayloadHandler.register();
    }
}
