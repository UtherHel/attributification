package dev.utherhel.attributification.client.gui;

import dev.utherhel.attributification.gui.MappingFileNames;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Displays the list of {@link EditableMapping} entries for one file and lets
 * the user add, remove, and edit them.  Clicking an entry opens
 * {@link MappingEntryScreen} for detailed editing.
 */
@Environment(EnvType.CLIENT)
class MappingEditorScreen extends Screen {

    private static final int HEADER_HEIGHT = 33;
    private static final int FOOTER_HEIGHT = 50;
    /** Total footer row width: 100 + 8 + save (60/110) + 8 + 60. */
    private static final int FOOTER_WIDTH_EDIT = 236;
    private static final int FOOTER_WIDTH_OVERRIDE = 286;

    private final MappingsScreen parent;
    private final String fileId;
    private final List<EditableMapping> entries;
    private final EditorMode mode;

    private EntryListWidget entryList;
    private ButtonWidget addButton, saveButton, backButton;

    MappingEditorScreen(
        MappingsScreen parent,
        String fileId,
        List<EditableMapping> entries,
        EditorMode mode
    ) {
        super(
            Text.translatable(
                mode.isOverride()
                    ? "gui.attributification.mappings.override.title"
                    : "gui.attributification.mappings.editor.title",
                MappingFileNames.fileNameOf(fileId)
            )
        );
        this.parent = parent;
        this.fileId = fileId;
        this.entries = new ArrayList<>(entries);
        this.mode = mode;
    }

    @Override
    protected void init() {
        entryList = new EntryListWidget();

        addButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.add_mapping"),
            b -> {
                entries.add(new EditableMapping());
                entryList.reload();
                entryList.scrollToBottom();
            }
        ).dimensions(0, 0, 100, 20).build();

        saveButton = ButtonWidget.builder(
            Text.translatable(
                mode.isOverride()
                    ? "gui.attributification.mappings.save_override"
                    : "gui.attributification.mappings.save"
            ),
            b -> save()
        ).dimensions(0, 0, mode.isOverride() ? 110 : 60, 20).build();

        if (mode.isOverride()) {
            saveButton.setTooltip(
                Tooltip.of(
                    Text.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
            );
        }

        backButton = ButtonWidget.builder(
            ScreenTexts.BACK, b -> close()
        ).dimensions(0, 0, 60, 20).build();

        addDrawableChild(entryList);
        addDrawableChild(addButton);
        addDrawableChild(saveButton);
        addDrawableChild(backButton);

        refreshPositions();
    }

    @Override
    protected void initTabNavigation() {
        // 1.20.1 calls this on every return to an already-initialized screen
        // and on resize, without clearing children first — so re-init would
        // duplicate every widget. Only repositioning is needed.
        refreshPositions();
    }

    /** Applies the footer/list layout for the current screen size. */
    private void refreshPositions() {
        entryList.updateSize(width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT);
        int footerWidth = mode.isOverride()
            ? FOOTER_WIDTH_OVERRIDE
            : FOOTER_WIDTH_EDIT;
        int saveWidth = mode.isOverride() ? 110 : 60;
        int footerX = (width - footerWidth) / 2;
        int footerY = height - FOOTER_HEIGHT + 15;
        addButton.setPosition(footerX, footerY);
        saveButton.setPosition(footerX + 108, footerY);
        backButton.setPosition(footerX + 108 + saveWidth + 8, footerY);
    }

    private void save() {
        String json = EditableMapping.serializeFile(entries);
        MappingsScreen.callbacks.saveFile(fileId, json);
        parent.onSaved(MappingFileNames.fileNameOf(fileId), mode);
        close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
            textRenderer,
            title,
            width / 2,
            12,
            0xFFFFFF
        );
    }

    // ── Entry list ────────────────────────────────────────────────────────

    private class EntryListWidget extends ElementListWidget<MappingRow> {

        EntryListWidget() {
            super(
                MinecraftClient.getInstance(),
                MappingEditorScreen.this.width,
                MappingEditorScreen.this.height,
                HEADER_HEIGHT,
                MappingEditorScreen.this.height - FOOTER_HEIGHT,
                28
            );
            reload();
        }

        @Override
        public int getRowWidth() {
            return Math.min(480, MappingEditorScreen.this.width - 60);
        }

        protected int getScrollbarPositionX() {
            return width / 2 + getRowWidth() / 2 + 10;
        }

        void reload() {
            clearEntries();
            for (int i = 0; i < entries.size(); i++) {
                addEntry(new MappingRow(i));
            }
        }

        void scrollToBottom() {
            setScrollAmount(getMaxScroll());
        }
    }

    private class MappingRow extends ElementListWidget.Entry<MappingRow> {

        private final int index;
        private final ButtonWidget editBtn;
        private final ButtonWidget removeBtn;

        MappingRow(int index) {
            this.index = index;
            this.editBtn = ButtonWidget.builder(
                Text.translatable("gui.attributification.mappings.edit"),
                b -> openEntry(index)
            )
                .width(50)
                .build();
            this.removeBtn = ButtonWidget.builder(
                Text.translatable(
                    "gui.attributification.mappings.remove_mapping"
                ),
                b -> removeEntry(index)
            )
                .width(50)
                .build();
            editBtn.active = true;
            removeBtn.active = true;
        }

        @Override
        public void render(
            DrawContext context,
            int idx,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float tickDelta
        ) {
            int mid = y + (entryHeight - 9) / 2;

            // Entry number badge
            context.drawTextWithShadow(
                MappingEditorScreen.this.textRenderer,
                Text.literal("#" + (index + 1)).formatted(Formatting.DARK_GRAY),
                x + 2,
                mid,
                0xFFFFFFFF
            );

            // Summary — clipped to avoid overlapping buttons
            int summaryX = x + 22;
            int summaryMaxX = x + entryWidth - 108;
            if (summaryMaxX > summaryX) {
                context.enableScissor(summaryX, y, summaryMaxX, y + entryHeight);
                context.drawTextWithShadow(
                    MappingEditorScreen.this.textRenderer,
                    entries.get(index).summary(),
                    summaryX,
                    mid,
                    0xFFFFFFFF
                );
                context.disableScissor();
            }

            // Buttons on the right
            int btnRight = x + entryWidth - 4;
            removeBtn.setPosition(btnRight - 50, y + (entryHeight - 20) / 2);
            removeBtn.render(context, mouseX, mouseY, tickDelta);
            editBtn.setPosition(
                btnRight - 104,
                y + (entryHeight - 20) / 2
            );
            editBtn.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(editBtn, removeBtn);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(editBtn, removeBtn);
        }
    }

    private void openEntry(int index) {
        client.setScreen(
            new MappingEntryScreen(this, entries.get(index), true)
        );
    }

    private void removeEntry(int index) {
        entries.remove(index);
        entryList.reload();
    }
}
