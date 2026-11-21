package dev.utherhel.attributification.client.gui;

import dev.utherhel.attributification.gui.MappingFileNames;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(
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
            Component.translatable(
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
        layout.addTitleHeader(title, font);

        entryList = layout.addToContents(new EntryListWidget());

        LinearLayout footer = layout.addToFooter(
            LinearLayout.horizontal().spacing(8)
        );

        footer.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.add_mapping"),
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
        Button saveButton = footer.addChild(
            Button.builder(
                Component.translatable(
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
                Tooltip.create(
                    Component.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
            );
        }

        footer.addChild(
            Button.builder(CommonComponents.GUI_BACK, b -> onClose()).width(60).build()
        );

        layout.visitWidgets(this::addRenderableWidget);
        refreshPositions();
    }

    @Override
    protected void repositionElements() {
        refreshPositions();
    }

    private void refreshPositions() {
        layout.arrangeElements();
        entryList.updateSize(width, layout);
    }

    private void save() {
        String json = EditableMapping.serializeFile(entries);
        MappingsScreen.callbacks.saveFile(fileId, json);
        parent.onSaved(MappingFileNames.fileNameOf(fileId), mode);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Entry list ────────────────────────────────────────────────────────

    private class EntryListWidget extends ContainerObjectSelectionList<MappingRow> {

        EntryListWidget() {
            super(
                Minecraft.getInstance(),
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

    private class MappingRow extends ContainerObjectSelectionList.Entry<MappingRow> {

        private final int index;
        private final Button editBtn;
        private final Button removeBtn;

        MappingRow(int index) {
            this.index = index;
            this.editBtn = Button.builder(
                Component.translatable("gui.attributification.mappings.edit"),
                b -> openEntry(index)
            )
                .width(50)
                .build();
            this.removeBtn = Button.builder(
                Component.translatable(
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
            GuiGraphics context,
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
            context.drawString(
                MappingEditorScreen.this.font,
                Component.literal("#" + (index + 1)).withStyle(ChatFormatting.DARK_GRAY),
                x + 2,
                mid,
                0xFFFFFFFF
            );

            // Summary — clipped to avoid overlapping buttons
            int summaryX = x + 22;
            int summaryMaxX = x + entryWidth - 108;
            if (summaryMaxX > summaryX) {
                context.enableScissor(summaryX, y, summaryMaxX, y + entryHeight);
                context.drawString(
                    MappingEditorScreen.this.font,
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
        public List<? extends GuiEventListener> children() {
            return List.of(editBtn, removeBtn);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(editBtn, removeBtn);
        }
    }

    private void openEntry(int index) {
        minecraft.setScreen(
            new MappingEntryScreen(this, entries.get(index), true)
        );
    }

    private void removeEntry(int index) {
        entries.remove(index);
        entryList.reload();
    }
}
