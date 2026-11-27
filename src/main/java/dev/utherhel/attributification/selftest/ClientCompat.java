package dev.utherhel.attributification.selftest;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The client calls of the self-test that differ between Minecraft versions (1.20.1 - 26.x, Stonecutter conditions;
 * the active version decides which branch is uncommented). Created by dev/modtest/sync.py when missing; owned by the mod.
 */
final class ClientCompat {
    private ClientCompat() {
    }

    static Screen screen(Minecraft mc) {
//? if >=26.2 {
/*        return mc.gui.screen();
*///?} else {
        return mc.screen;
//?}
    }

    /** The loading overlay (resource reload), null when none is shown. */
    static Overlay overlay(Minecraft mc) {
//? if >=26.2 {
/*        return mc.gui.overlay();
*///?} else {
        return mc.getOverlay();
//?}
    }

    static boolean onTitleScreen(Minecraft mc) {
        return screen(mc) instanceof TitleScreen && overlay(mc) == null;
    }

    static boolean inWorld(Minecraft mc) {
        return screen(mc) == null && overlay(mc) == null;
    }

    static String screenName(Minecraft mc) {
        return screen(mc) == null ? "none" : screen(mc).getClass().getName();
    }

    static void screenshot(Minecraft mc, Path out) throws Exception {
        Files.createDirectories(out.getParent());
//? if >=26.2 {
/*        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> write(img, out));
*///?} else if >=1.21.5 {
/*        Screenshot.takeScreenshot(mc.getMainRenderTarget(), img -> write(img, out));
*///?} else {
        write(Screenshot.takeScreenshot(mc.getMainRenderTarget()), out);
//?}
    }

    private static void write(NativeImage img, Path out) {
        try (img) {
            img.writeToFile(out);
        } catch (Exception e) {
            System.out.println("[ModTest] screenshot write failed: " + e);
        }
    }
}
