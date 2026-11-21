package dev.utherhel.attributification.client.gui.compat;

//? if >=1.20.5 {
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}

/**
 * Screen base that hides the version-specific render and key signatures.
 * Subclasses draw extra text in {@link #renderOverlay} (after widgets) and handle keys in
 * {@link #onKeyPressed}; returning {@code false} falls back to the vanilla handling.
 */
public abstract class CompatScreen extends Screen {

    protected CompatScreen(Component title) {
        super(title);
    }

    protected void renderOverlay(GuiGfx gfx, int mouseX, int mouseY, float delta) {}

    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        renderOverlay(new GuiGfx(g), mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        renderOverlay(new GuiGfx(g), mouseX, mouseY, delta);
    }
    //?}

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        // 26.3 dropped the scan code from KeyEvent; the screens only look at the key code
        return onKeyPressed(event.key(), 0, event.modifiers()) || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}
}
//?}
