package dev.utherhel.attributification.client.gui;

import dev.utherhel.attributification.gui.MappingFileNames;
import dev.utherhel.attributification.gui.network.MappingFileEntry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Root screen of the mappings GUI: browses the mapping datapack files that the
 * server reported and opens {@link MappingEditorScreen} for one of them.
 * <p>
 * Layout follows the vanilla three-part convention (header / scrolling body /
 * footer) so nothing is ever drawn on top of a widget, and all networking for
 * the whole GUI is funnelled through the static {@code deliver*} methods.
 */
@Environment(EnvType.CLIENT)
public class MappingsScreen extends Screen {

    // ── Platform bridge ───────────────────────────────────────────────────

    public interface Callbacks {
        void requestFiles();
        void requestFileContent(String fileName);
        void saveFile(String fileName, String jsonContent);
        void deleteFile(String fileName);
        void requestAttributesList();
        void convertLegacyMappings();
    }

    /** Replaced by each loader's client setup; no-op until then. */
    public static Callbacks callbacks = new Callbacks() {
        @Override
        public void requestFiles() {}

        @Override
        public void requestFileContent(String fileName) {}

        @Override
        public void saveFile(String fileName, String jsonContent) {}

        @Override
        public void deleteFile(String fileName) {}

        @Override
        public void requestAttributesList() {}

        @Override
        public void convertLegacyMappings() {}
    };

    // ── Shared client state ───────────────────────────────────────────────

    /**
     * Attribute IDs from the server. Cached statically so the search popup
     * works no matter which sub-screen is open when the packet arrives.
     */
    private static List<String> attributeCache = List.of();

    private static int legacyMappingsCount;

    static List<String> attributes() {
        return attributeCache;
    }

    static void requestAttributesIfMissing() {
        if (attributeCache.isEmpty()) callbacks.requestAttributesList();
    }

    // ── Static delivery (called from the loader payload handlers) ─────────

    public static void deliverOpen() {
        Minecraft.getInstance().setScreen(new MappingsScreen());
    }

    public static void deliverFilesList(List<MappingFileEntry> files) {
        if (current() instanceof MappingsScreen screen) {
            screen.onFilesList(files);
        }
    }

    public static void deliverFileContent(String fileName, String json) {
        if (current() instanceof MappingsScreen screen) {
            screen.onFileContent(fileName, json);
        }
    }

    public static void deliverAttributes(List<String> attributes) {
        attributeCache = List.copyOf(attributes);
    }

    public static void deliverLegacyCount(int count) {
        legacyMappingsCount = count;
        if (current() instanceof MappingsScreen screen) {
            screen.refreshButtons();
            if (count > 0) {
                screen.setStatus(
                    Component.translatable(
                        "gui.attributification.mappings.status.legacy_found",
                        count
                    )
                );
            }
        }
    }

    private static Screen current() {
        return Minecraft.getInstance().screen;
    }

    // ── Instance state ────────────────────────────────────────────────────

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(
        this,
        33,
        60
    );

    private FileListWidget fileList;
    private EditBox newFileField;
    private Button createButton, editButton, deleteButton, convertButton;

    private List<MappingFileEntry> files = List.of();
    private Component status = Component.empty();
    private int statusTimer;

    public MappingsScreen() {
        super(Component.translatable("gui.attributification.mappings.title"));
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        fileList = layout.addToContents(new FileListWidget());

        // Footer row 1: new-file name field + Create.
        LinearLayout top = LinearLayout.horizontal()
            .spacing(8);
        newFileField = new EditBox(
            font,
            0,
            0,
            168,
            20,
            Component.translatable("gui.attributification.mappings.file_name")
        );
        newFileField.setMaxLength(MappingFileNames.MAX_NAME_LENGTH);
        newFileField.setHint(
            Component.translatable(
                "gui.attributification.mappings.file_name_hint"
            ).withStyle(ChatFormatting.DARK_GRAY)
        );
        // Latin letters, digits, '_' and '-' only: Minecraft rejects anything
        // else in a datapack path, which would break the whole pack.
        newFileField.setFilter(MappingFileNames::isTypeable);
        newFileField.setResponder(value -> refreshButtons());
        top.addChild(newFileField);

        createButton = top.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.new_file"),
                b -> createFile()
            )
                .width(80)
                .build()
        );

        // Footer row 2: actions on the selected file.
        LinearLayout bottom = LinearLayout.horizontal()
            .spacing(8);

        editButton = bottom.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.edit"),
                b -> openSelected()
            )
                .width(74)
                .build()
        );

        deleteButton = bottom.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.delete"),
                b -> confirmDelete()
            )
                .width(74)
                .build()
        );

        convertButton = bottom.addChild(
            Button.builder(
                Component.translatable(
                    "gui.attributification.mappings.convert_legacy",
                    legacyMappingsCount
                ),
                b -> callbacks.convertLegacyMappings()
            )
                .width(96)
                .build()
        );

        bottom.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.refresh"),
                b -> callbacks.requestFiles()
            )
                .width(64)
                .build()
        );

        bottom.addChild(
            Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(58).build()
        );

        LinearLayout footer = layout.addToFooter(
            LinearLayout.vertical().spacing(6)
        );
        footer.addChild(top);
        footer.addChild(bottom);

        layout.visitWidgets(this::addRenderableWidget);
        refreshPositions();
        refreshButtons();

        callbacks.requestFiles();
        requestAttributesIfMissing();
    }

    @Override
    protected void repositionElements() {
        refreshPositions();
    }

    private void refreshPositions() {
        layout.arrangeElements();
        fileList.updateSize(width, layout);
    }

    /** Keeps button enablement in sync with the current selection. */
    private void refreshButtons() {
        MappingFileEntry selected = selectedFile();
        editButton.active = selected != null;
        deleteButton.active = selected != null && selected.editable();
        createButton.active = !newFileField.getValue().isBlank();

        // A read-only file cannot be edited in place, but it can be overridden
        // by a copy in the world datapack — so the button offers that instead.
        boolean override = selected != null && !selected.editable();
        editButton.setMessage(
            Component.translatable(
                override
                    ? "gui.attributification.mappings.override"
                    : "gui.attributification.mappings.edit"
            )
        );
        editButton.setTooltip(
            override
                ? Tooltip.create(
                    Component.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
                : null
        );

        boolean hasLegacy = legacyMappingsCount > 0;
        convertButton.visible = hasLegacy;
        convertButton.active = hasLegacy;
        convertButton.setMessage(
            Component.translatable(
                "gui.attributification.mappings.convert_legacy",
                legacyMappingsCount
            )
        );

        if (override) {
            deleteButton.setTooltip(
                Tooltip.create(
                    Component.translatable(
                        "gui.attributification.mappings.read_only_tooltip"
                    )
                )
            );
        } else {
            deleteButton.setTooltip(null);
        }
    }

    private MappingFileEntry selectedFile() {
        FileEntry entry = fileList == null ? null : fileList.getSelected();
        return entry == null ? null : entry.file;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void createFile() {
        String fileName = MappingFileNames.normalizeTypedName(
            newFileField.getValue()
        );
        if (fileName == null) {
            setStatus(
                Component.translatable(
                    "gui.attributification.mappings.status.invalid_name"
                ).withStyle(ChatFormatting.RED)
            );
            return;
        }
        String id = MappingFileNames.defaultId(fileName);
        for (MappingFileEntry file : files) {
            if (file.fileName().equalsIgnoreCase(id)) {
                setStatus(
                    Component.translatable(
                        "gui.attributification.mappings.status.exists",
                        fileName
                    ).withStyle(ChatFormatting.RED)
                );
                return;
            }
        }
        newFileField.setValue("");
        List<EditableMapping> entries = new ArrayList<>();
        entries.add(new EditableMapping());
        minecraft.setScreen(
            new MappingEditorScreen(this, id, entries, EditorMode.EDIT)
        );
    }

    private void openSelected() {
        MappingFileEntry file = selectedFile();
        if (file == null) return;
        setStatus(Component.translatable("gui.attributification.mappings.loading"));
        callbacks.requestFileContent(file.fileName());
        requestAttributesIfMissing();
    }

    private void confirmDelete() {
        MappingFileEntry file = selectedFile();
        if (file == null || !file.editable()) return;
        minecraft.setScreen(
            new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        callbacks.deleteFile(file.fileName());
                        setStatus(
                            Component.translatable(
                                "gui.attributification.mappings.status.deleted"
                            )
                        );
                        callbacks.requestFiles();
                    }
                    minecraft.setScreen(this);
                },
                Component.translatable(
                    "gui.attributification.mappings.confirm_delete"
                ),
                Component.literal(file.fileName())
            )
        );
    }

    // ── Data in ───────────────────────────────────────────────────────────

    private void onFilesList(List<MappingFileEntry> newFiles) {
        this.files = List.copyOf(newFiles);
        String previous = selectedFile() == null
            ? null
            : selectedFile().fileName();
        fileList.reload(files, previous);
        refreshButtons();
    }

    private void onFileContent(String fileName, String json) {
        MappingFileEntry file = null;
        for (MappingFileEntry candidate : files) {
            if (candidate.fileName().equals(fileName)) {
                file = candidate;
                break;
            }
        }
        // Unknown files are treated as read-only: saving one still works, it
        // just lands in the world datapack as an override.
        EditorMode mode = file != null && file.editable()
            ? EditorMode.EDIT
            : EditorMode.OVERRIDE;

        List<EditableMapping> entries;
        try {
            entries = EditableMapping.parseFile(json);
        } catch (Exception e) {
            setStatus(
                Component.translatable(
                    "gui.attributification.mappings.status.parse_error",
                    String.valueOf(e.getMessage())
                ).withStyle(ChatFormatting.RED)
            );
            return;
        }
        minecraft.setScreen(
            new MappingEditorScreen(this, fileName, entries, mode)
        );
    }

    /** Called by the editor after a successful save. */
    void onSaved(String fileName, EditorMode mode) {
        setStatus(
            Component.translatable(
                mode.isOverride()
                    ? "gui.attributification.mappings.status.overridden"
                    : "gui.attributification.mappings.status.saved",
                fileName
            ).withStyle(ChatFormatting.GREEN)
        );
        callbacks.requestFiles();
    }

    void setStatus(Component text) {
        this.status = text;
        this.statusTimer = 160;
    }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (files.isEmpty()) {
            context.drawCenteredString(
                font,
                Component.translatable("gui.attributification.mappings.no_files"),
                width / 2,
                layout.getHeaderHeight() + 24,
                0xFFA0A0A0
            );
        }

        if (statusTimer > 0 && status != Component.empty()) {
            context.drawCenteredString(
                font,
                status,
                width / 2,
                layout.getHeaderHeight() - 14,
                0xFFFFFFFF
            );
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTimer > 0) statusTimer--;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── File list ─────────────────────────────────────────────────────────

    private class FileListWidget
        extends ObjectSelectionList<FileEntry> {

        FileListWidget() {
            super(
                Minecraft.getInstance(),
                MappingsScreen.this.width,
                MappingsScreen.this.layout.getContentHeight(),
                MappingsScreen.this.layout.getHeaderHeight(),
                24
            );
        }

        @Override
        public int getRowWidth() {
            return Math.min(320, MappingsScreen.this.width - 80);
        }

        void reload(List<MappingFileEntry> files, String reselect) {
            clearEntries();
            for (MappingFileEntry file : files) {
                FileEntry entry = new FileEntry(file);
                addEntry(entry);
                if (file.fileName().equals(reselect)) setSelected(entry);
            }
        }

        @Override
        public void setSelected(FileEntry entry) {
            super.setSelected(entry);
            MappingsScreen.this.refreshButtons();
        }
    }

    private class FileEntry extends ObjectSelectionList.Entry<FileEntry> {

        private final MappingFileEntry file;
        private long lastClickTime;

        FileEntry(MappingFileEntry file) {
            this.file = file;
        }

        @Override
        public Component getNarration() {
            return Component.literal(file.fileName());
        }

        @Override
        public void render(
            GuiGraphics context,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float tickDelta
        ) {
            var textRenderer = MappingsScreen.this.font;
            int textY = y + (entryHeight - 9) / 2;

            // Right-hand badge first, so the name can be clipped against it.
            Component badge = file.editable()
                ? Component.translatable("gui.attributification.mappings.world")
                : Component.translatable("gui.attributification.mappings.read_only");
            int badgeWidth = textRenderer.width(badge);
            context.drawString(
                textRenderer,
                badge,
                x + entryWidth - badgeWidth - 4,
                textY,
                file.editable() ? 0xFF70B070 : 0xFF808080
            );

            // "namespace/name.json" — the namespace is dimmed, since it only
            // matters when two datapacks ship a file of the same name.
            String id = file.fileName();
            String namespace = MappingFileNames.namespaceOf(id) + "/";
            String name = MappingFileNames.fileNameOf(id);

            int nameLimit = x + entryWidth - badgeWidth - 10;
            context.enableScissor(x + 4, y, Math.max(x + 4, nameLimit), y + entryHeight);
            int nsX = context.drawString(
                textRenderer,
                Component.literal(namespace).withStyle(ChatFormatting.DARK_GRAY),
                x + 4,
                textY,
                0xFF555555
            );
            context.drawString(
                textRenderer,
                Component.literal(name),
                nsX,
                textY,
                file.editable() ? 0xFFFFFFFF : 0xFFA0A0A0
            );
            context.disableScissor();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            MappingsScreen.this.fileList.setSelected(this);
            long now = Util.getMillis();
            if (now - lastClickTime < 250L) {
                MappingsScreen.this.openSelected();
            }
            lastClickTime = now;
            return true;
        }
    }
}
