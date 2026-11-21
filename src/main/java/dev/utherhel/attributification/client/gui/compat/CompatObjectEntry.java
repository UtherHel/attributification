package dev.utherhel.attributification.client.gui.compat;

//? if >=1.20.5 {
import net.minecraft.client.gui.components.ObjectSelectionList;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

/**
 * {@link ObjectSelectionList.Entry} with one render and click signature for all versions.
 * {@code x}/{@code y}/{@code width}/{@code height} are the row's content area.
 */
public abstract class CompatObjectEntry<E extends CompatObjectEntry<E>> extends ObjectSelectionList.Entry<E> {

    protected abstract void renderEntry(
        GuiGfx gfx, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered, float delta
    );

    /** Handles a click on the row; the default keeps the vanilla behaviour. */
    protected boolean onClick(double mouseX, double mouseY, int button) {
        return vanillaClick(mouseX, mouseY, button);
    }

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

    //? if >=1.21.9 {
    /*private MouseButtonEvent pendingEvent;
    private boolean pendingDoubleClick;

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        pendingEvent = event;
        pendingDoubleClick = doubleClick;
        return onClick(event.x(), event.y(), event.button());
    }

    protected final boolean vanillaClick(double mouseX, double mouseY, int button) {
        return super.mouseClicked(pendingEvent, pendingDoubleClick);
    }
    *///?} else {
    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onClick(mouseX, mouseY, button);
    }

    protected final boolean vanillaClick(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}
}
//?}
