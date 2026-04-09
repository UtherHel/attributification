package dev.utherhel.attributification.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import dev.utherhel.attributification.Attributifaction;

@Mod(Attributifaction.MOD_ID)
public final class AttributificationForge {
    public AttributificationForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Attributifaction.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Attributifaction.init();
    }
}
