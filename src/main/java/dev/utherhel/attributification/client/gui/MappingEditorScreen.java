package dev.utherhel.attributification.client.gui;

//? if >=1.20.5 {
import dev.utherhel.attributification.gui.MappingFileNames;
import java.util.ArrayList;
import java.util.List;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import dev.utherhel.attributification.client.gui.compat.*;
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
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
class MappingEditorScreen extends CompatScreen {

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
        if (!dev.utherhel.attributification.gui.network.JsonText.fitsToServer(json)) {
            // too big for one packet to the server: keep the editor open instead of losing the connection
            if (minecraft != null && minecraft.player != null) {
                //? if >=26.1 {
                /*minecraft.player.sendSystemMessage(Component.translatable(
                    "gui.attributification.mappings.too_large", entries.size()));
                *///?} else {
                minecraft.player.displayClientMessage(Component.translatable(
                    "gui.attributification.mappings.too_large", entries.size()), false);
                //?}
            }
            return;
        }
        MappingsScreen.callbacks.saveFile(fileId, json);
        parent.onSaved(MappingFileNames.fileNameOf(fileId), mode);
        onClose();
    }

    @Override
    public void onClose() {
        ClientScreens.open(parent);
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
            //? if >=1.21.4 {
            /*setScrollAmount(maxScrollAmount());
            *///?} else {
            setScrollAmount(getMaxScroll());
            //?}
        }
    }

    private class MappingRow extends CompatContainerEntry<MappingRow> {

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
        protected void renderEntry(
            GuiGfx context,
            int x,
            int y,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float tickDelta
        ) {
            int mid = y + (entryHeight - 9) / 2;

            // Entry number badge
            context.text(
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
                context.text(
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
            context.render(removeBtn, mouseX, mouseY, tickDelta);
            editBtn.setPosition(
                btnRight - 104,
                y + (entryHeight - 20) / 2
            );
            context.render(editBtn, mouseX, mouseY, tickDelta);
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
        ClientScreens.open(
            new MappingEntryScreen(this, entries.get(index), true)
        );
    }

    private void removeEntry(int index) {
        entries.remove(index);
        entryList.reload();
    }
}
//?} else {
/*import dev.utherhel.attributification.gui.MappingFileNames;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/^*
 * Displays the list of {@link EditableMapping} entries for one file and lets
 * the user add, remove, and edit them.  Clicking an entry opens
 * {@link MappingEntryScreen} for detailed editing.
 ^/
class MappingEditorScreen extends Screen {

    private static final int HEADER_HEIGHT = 33;
    private static final int FOOTER_HEIGHT = 50;
    /^* Total footer row width: 100 + 8 + save (60/110) + 8 + 60. ^/
    private static final int FOOTER_WIDTH_EDIT = 236;
    private static final int FOOTER_WIDTH_OVERRIDE = 286;

    private final MappingsScreen parent;
    private final String fileId;
    private final List<EditableMapping> entries;
    private final EditorMode mode;

    private EntryListWidget entryList;
    private Button addButton, saveButton, backButton;

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
        entryList = new EntryListWidget();

        addButton = Button.builder(
            Component.translatable("gui.attributification.mappings.add_mapping"),
            b -> {
                entries.add(new EditableMapping());
                entryList.reload();
                entryList.scrollToBottom();
            }
        ).bounds(0, 0, 100, 20).build();

        saveButton = Button.builder(
            Component.translatable(
                mode.isOverride()
                    ? "gui.attributification.mappings.save_override"
                    : "gui.attributification.mappings.save"
            ),
            b -> save()
        ).bounds(0, 0, mode.isOverride() ? 110 : 60, 20).build();

        if (mode.isOverride()) {
            saveButton.setTooltip(
                Tooltip.create(
                    Component.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
            );
        }

        backButton = Button.builder(
            CommonComponents.GUI_BACK, b -> onClose()
        ).bounds(0, 0, 60, 20).build();

        addRenderableWidget(entryList);
        addRenderableWidget(addButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(backButton);

        refreshPositions();
    }

    @Override
    protected void repositionElements() {
        // 1.20.1 calls this on every return to an already-initialized screen
        // and on resize, without clearing children first — so re-init would
        // duplicate every widget. Only repositioning is needed.
        refreshPositions();
    }

    /^* Applies the footer/list layout for the current screen size. ^/
    private void refreshPositions() {
        //? if >=1.20.3 {
        entryList.setRectangle(width, height - HEADER_HEIGHT - FOOTER_HEIGHT, 0, HEADER_HEIGHT);
        //?} else {
        /^entryList.updateSize(width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT);
        ^///?}
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
        if (!dev.utherhel.attributification.gui.network.JsonText.fitsToServer(json)) {
            // too big for one packet to the server: keep the editor open instead of losing the connection
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                    "gui.attributification.mappings.too_large", entries.size()), false);
            }
            return;
        }
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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(
            font,
            title,
            width / 2,
            12,
            0xFFFFFF
        );
    }

    // ── Entry list ────────────────────────────────────────────────────────

    private class EntryListWidget extends ContainerObjectSelectionList<MappingRow> {

        EntryListWidget() {
            super(
                Minecraft.getInstance(),
                MappingEditorScreen.this.width,
                //? if >=1.20.3 {
                MappingEditorScreen.this.height - HEADER_HEIGHT - FOOTER_HEIGHT,
                HEADER_HEIGHT,
                //?} else {
                /^MappingEditorScreen.this.height,
                HEADER_HEIGHT,
                MappingEditorScreen.this.height - FOOTER_HEIGHT,
                ^///?}
                28
            );
            reload();
        }

        @Override
        public int getRowWidth() {
            return Math.min(480, MappingEditorScreen.this.width - 60);
        }

        protected int getScrollbarPosition() {
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
*///?}
