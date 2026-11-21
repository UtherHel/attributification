package dev.utherhel.attributification.client.gui.compat;

//? if >=1.21 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening and reading the current screen: {@code Minecraft} up to 26.1, {@code Minecraft.gui} from 26.2. */
public final class ClientScreens {

    private ClientScreens() {}

    public static void open(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    public static Screen current() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }
}
//?}
