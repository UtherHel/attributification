package dev.utherhel.attributification.client.gui.compat;

//? if >=1.20.5 {
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * {@link ContainerObjectSelectionList.Entry} with one render signature for all versions.
 * {@code x}/{@code y}/{@code width}/{@code height} are the row's content area.
 * Clicks go to the row's children (vanilla behaviour).
 */
public abstract class CompatContainerEntry<E extends CompatContainerEntry<E>>
    extends ContainerObjectSelectionList.Entry<E> {

    protected abstract void renderEntry(
        GuiGfx gfx, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered, float delta
    );

    //? if >=26.1 {
    /*@Override
    public final void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
        renderEntry(new GuiGfx(g), getContentX(), getContentY(), getContentWidth(), getContentHeight(),
            mouseX, mouseY, hovered, delta);
    }
    *///?} else if >=1.21.9 {
    /*@Override
    public final void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float delta) {
        renderEntry(new GuiGfx(g), getContentX(), getContentY(), getContentWidth(), getContentHeight(),
            mouseX, mouseY, hovered, delta);
    }
    *///?} else {
    @Override
    public final void render(GuiGraphics g, int index, int y, int x, int width, int height,
                             int mouseX, int mouseY, boolean hovered, float delta) {
        renderEntry(new GuiGfx(g), x, y, width, height, mouseX, mouseY, hovered, delta);
    }
    //?}
}
//?}
