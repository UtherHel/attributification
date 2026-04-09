package dev.utherhel.attributification.neoforge;

import dev.utherhel.attributification.Attributifaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Attributifaction.MOD_ID)
public final class AttributificationNeoForge {
    public AttributificationNeoForge(IEventBus modEventBus) {
        Attributifaction.init();
    }
}
