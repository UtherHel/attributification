package dev.utherhel.attributification.fabric.client;

//? if fabric {
import dev.kikugie.fletching_table.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;

@Entrypoint(Entrypoint.CLIENT)
public final class AttributificationFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    }
}
//?}
