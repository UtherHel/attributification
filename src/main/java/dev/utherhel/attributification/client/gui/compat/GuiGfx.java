package dev.utherhel.attributification.client.gui.compat;

//? if >=1.20.5 {
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * The few drawing calls the editor screens need, over the version's draw context:
 * {@code GuiGraphics} up to 1.21.x, {@code GuiGraphicsExtractor} from 26.1.
 */
public final class GuiGfx {

    //? if >=26.1 {
    /*private final GuiGraphicsExtractor g;

    public GuiGfx(GuiGraphicsExtractor g) {
        this.g = g;
    }
    *///?} else {
    private final GuiGraphics g;

    public GuiGfx(GuiGraphics g) {
        this.g = g;
    }
    //?}

    /** Draws text with a shadow and returns the x right after it. */
    public int text(Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color);
        return x + font.width(text);
        *///?} else if >=1.21.6 {
        /*g.drawString(font, text, x, y, color);
        return x + font.width(text);
        *///?} else {
        return g.drawString(font, text, x, y, color);
        //?}
    }

    public void centeredText(Font font, Component text, int centerX, int y, int color) {
        //? if >=26.1 {
        /*g.centeredText(font, text, centerX, y, color);
        *///?} else {
        g.drawCenteredString(font, text, centerX, y, color);
        //?}
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        g.disableScissor();
    }

    /** Renders a child widget that is not registered with the screen (list rows own theirs). */
    public void render(Renderable widget, int mouseX, int mouseY, float delta) {
        //? if >=26.1 {
        /*widget.extractRenderState(g, mouseX, mouseY, delta);
        *///?} else {
        widget.render(g, mouseX, mouseY, delta);
        //?}
    }
}
//?}
