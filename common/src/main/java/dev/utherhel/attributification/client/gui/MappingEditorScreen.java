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
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
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

    private final MappingsScreen parent;
    private final String fileId;
    private final List<EditableMapping> entries;
    private final EditorMode mode;

    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(
        this,
        33,
        50
    );

    private EntryListWidget entryList;

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
        layout.addHeader(title, textRenderer);

        entryList = layout.addBody(new EntryListWidget());

        DirectionalLayoutWidget footer = layout.addFooter(
            DirectionalLayoutWidget.horizontal().spacing(8)
        );

        footer.add(
            ButtonWidget.builder(
                Text.translatable("gui.attributification.mappings.add_mapping"),
                b -> {
                    entries.add(new EditableMapping());
                    entryList.reload();
                    entryList.scrollToBottom();
                }
            )
                .width(100)
                .build()
        );

        // Saving is available in both modes; for a read-only source it writes
        // an overriding copy into the world datapack rather than editing it.
        ButtonWidget saveButton = footer.add(
            ButtonWidget.builder(
                Text.translatable(
                    mode.isOverride()
                        ? "gui.attributification.mappings.save_override"
                        : "gui.attributification.mappings.save"
                ),
                b -> save()
            )
                .width(mode.isOverride() ? 110 : 60)
                .build()
        );
        if (mode.isOverride()) {
            saveButton.setTooltip(
                Tooltip.of(
                    Text.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
            );
        }

        footer.add(
            ButtonWidget.builder(ScreenTexts.BACK, b -> close()).width(60).build()
        );

        layout.forEachChild(this::addDrawableChild);
        refreshPositions();
    }

    @Override
    protected void initTabNavigation() {
        refreshPositions();
    }

    private void refreshPositions() {
        layout.refreshPositions();
        entryList.position(width, layout);
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

    // ── Entry list ────────────────────────────────────────────────────────

    private class EntryListWidget extends ElementListWidget<MappingRow> {

        EntryListWidget() {
            super(
                MinecraftClient.getInstance(),
                MappingEditorScreen.this.width,
                MappingEditorScreen.this.layout.getContentHeight(),
                MappingEditorScreen.this.layout.getHeaderHeight(),
                28
            );
            reload();
        }

        @Override
        public int getRowWidth() {
            return Math.min(480, MappingEditorScreen.this.width - 60);
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
