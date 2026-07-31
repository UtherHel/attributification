package dev.utherhel.attributification.fabric;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.config.LogLevel;
import dev.utherhel.attributification.fabric.config.AttributificationFabricConfig;
import dev.utherhel.attributification.fabric.gui.AttributificationFabricCommand;
import dev.utherhel.attributification.fabric.gui.AttributificationFabricPayloadHandler;
import net.fabricmc.api.ModInitializer;

public final class AttributificationFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LogLevel logLevel = AttributificationFabricConfig.loadLogLevel();
        Attributifaction.init(logLevel);
        AttributificationFabricCommand.register();
        AttributificationFabricPayloadHandler.register();
    }
}
