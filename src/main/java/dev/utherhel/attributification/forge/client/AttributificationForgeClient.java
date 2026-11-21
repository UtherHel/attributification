package dev.utherhel.attributification.forge.client;

//? if forge || (neoforge && <1.20.5) {
/*import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.forge.client.gui.AttributificationClientGuiSetup;
//? if forge {
/^import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
^///?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//?}

@Mod.EventBusSubscriber(modid = Attributifaction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AttributificationForgeClient {
    private AttributificationForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AttributificationClientGuiSetup::init);
    }
}
*///?}
