package dev.utherhel.attributification.client.gui;

import dev.utherhel.attributification.gui.MappingFileNames;
import dev.utherhel.attributification.gui.network.MappingFileEntry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

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
        MinecraftClient.getInstance().setScreen(new MappingsScreen());
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
                    Text.translatable(
                        "gui.attributification.mappings.status.legacy_found",
                        count
                    )
                );
            }
        }
    }

    private static Screen current() {
        return MinecraftClient.getInstance().currentScreen;
    }

    // ── Instance state ────────────────────────────────────────────────────

    private static final int HEADER_HEIGHT = 33;
    private static final int FOOTER_HEIGHT = 60;
    /** Total width of the footer's row 2 — the grid column both rows share. */
    private static final int ROW_WIDTH = 398;

    private FileListWidget fileList;
    private TextFieldWidget newFileField;
    private ButtonWidget createButton, editButton, deleteButton, convertButton;
    private ButtonWidget refreshButton, doneButton;

    private List<MappingFileEntry> files = List.of();
    private Text status = Text.empty();
    private int statusTimer;

    public MappingsScreen() {
        super(Text.translatable("gui.attributification.mappings.title"));
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        fileList = new FileListWidget();

        newFileField = new TextFieldWidget(
            textRenderer,
            0,
            0,
            168,
            20,
            Text.translatable("gui.attributification.mappings.file_name")
        );
        newFileField.setMaxLength(MappingFileNames.MAX_NAME_LENGTH);
        newFileField.setPlaceholder(
            Text.translatable(
                "gui.attributification.mappings.file_name_hint"
            ).formatted(Formatting.DARK_GRAY)
        );
        newFileField.setTextPredicate(MappingFileNames::isTypeable);
        newFileField.setChangedListener(value -> refreshButtons());

        createButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.new_file"),
            b -> createFile()
        ).dimensions(0, 0, 80, 20).build();

        editButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.edit"),
            b -> openSelected()
        ).dimensions(0, 0, 74, 20).build();

        deleteButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.delete"),
            b -> confirmDelete()
        ).dimensions(0, 0, 74, 20).build();

        convertButton = ButtonWidget.builder(
            Text.translatable(
                "gui.attributification.mappings.convert_legacy",
                legacyMappingsCount
            ),
            b -> callbacks.convertLegacyMappings()
        ).dimensions(0, 0, 96, 20).build();

        refreshButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.refresh"),
            b -> callbacks.requestFiles()
        ).dimensions(0, 0, 64, 20).build();

        doneButton = ButtonWidget.builder(
            ScreenTexts.DONE, b -> close()
        ).dimensions(0, 0, 58, 20).build();

        addDrawableChild(fileList);
        addDrawableChild(newFileField);
        addDrawableChild(createButton);
        addDrawableChild(editButton);
        addDrawableChild(deleteButton);
        addDrawableChild(convertButton);
        addDrawableChild(refreshButton);
        addDrawableChild(doneButton);

        refreshPositions();
        refreshButtons();

        callbacks.requestFiles();
        requestAttributesIfMissing();
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
        int footerX = (width - ROW_WIDTH) / 2;
        int row1Y = height - FOOTER_HEIGHT + 7;
        int row2Y = height - FOOTER_HEIGHT + 33;
        fileList.updateSize(width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT);
        newFileField.setPosition(footerX, row1Y);
        createButton.setPosition(footerX + 176, row1Y);
        editButton.setPosition(footerX, row2Y);
        deleteButton.setPosition(footerX + 82, row2Y);
        convertButton.setPosition(footerX + 164, row2Y);
        refreshButton.setPosition(footerX + 268, row2Y);
        doneButton.setPosition(footerX + 340, row2Y);
    }

    /** Keeps button enablement in sync with the current selection. */
    private void refreshButtons() {
        MappingFileEntry selected = selectedFile();
        editButton.active = selected != null;
        deleteButton.active = selected != null && selected.editable();
        createButton.active = !newFileField.getText().isBlank();

        boolean override = selected != null && !selected.editable();
        editButton.setMessage(
            Text.translatable(
                override
                    ? "gui.attributification.mappings.override"
                    : "gui.attributification.mappings.edit"
            )
        );
        editButton.setTooltip(
            override
                ? Tooltip.of(
                    Text.translatable(
                        "gui.attributification.mappings.override_tooltip"
                    )
                )
                : null
        );

        boolean hasLegacy = legacyMappingsCount > 0;
        convertButton.visible = hasLegacy;
        convertButton.active = hasLegacy;
        convertButton.setMessage(
            Text.translatable(
                "gui.attributification.mappings.convert_legacy",
                legacyMappingsCount
            )
        );

        if (override) {
            deleteButton.setTooltip(
                Tooltip.of(
                    Text.translatable(
                        "gui.attributification.mappings.read_only_tooltip"
                    )
                )
            );
        } else {
            deleteButton.setTooltip(null);
        }
    }

    private MappingFileEntry selectedFile() {
        FileEntry entry = fileList == null ? null : fileList.getSelectedOrNull();
        return entry == null ? null : entry.file;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void createFile() {
        String fileName = MappingFileNames.normalizeTypedName(
            newFileField.getText()
        );
        if (fileName == null) {
            setStatus(
                Text.translatable(
                    "gui.attributification.mappings.status.invalid_name"
                ).formatted(Formatting.RED)
            );
            return;
        }
        String id = MappingFileNames.defaultId(fileName);
        for (MappingFileEntry file : files) {
            if (file.fileName().equalsIgnoreCase(id)) {
                setStatus(
                    Text.translatable(
                        "gui.attributification.mappings.status.exists",
                        fileName
                    ).formatted(Formatting.RED)
                );
                return;
            }
        }
        newFileField.setText("");
        List<EditableMapping> entries = new ArrayList<>();
        entries.add(new EditableMapping());
        client.setScreen(
            new MappingEditorScreen(this, id, entries, EditorMode.EDIT)
        );
    }

    private void openSelected() {
        MappingFileEntry file = selectedFile();
        if (file == null) return;
        setStatus(Text.translatable("gui.attributification.mappings.loading"));
        callbacks.requestFileContent(file.fileName());
        requestAttributesIfMissing();
    }

    private void confirmDelete() {
        MappingFileEntry file = selectedFile();
        if (file == null || !file.editable()) return;
        client.setScreen(
            new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        callbacks.deleteFile(file.fileName());
                        setStatus(
                            Text.translatable(
                                "gui.attributification.mappings.status.deleted"
                            )
                        );
                        callbacks.requestFiles();
                    }
                    client.setScreen(this);
                },
                Text.translatable(
                    "gui.attributification.mappings.confirm_delete"
                ),
                Text.literal(file.fileName())
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
        EditorMode mode = file != null && file.editable()
            ? EditorMode.EDIT
            : EditorMode.OVERRIDE;

        List<EditableMapping> entries;
        try {
            entries = EditableMapping.parseFile(json);
        } catch (Exception e) {
            setStatus(
                Text.translatable(
                    "gui.attributification.mappings.status.parse_error",
                    String.valueOf(e.getMessage())
                ).formatted(Formatting.RED)
            );
            return;
        }
        client.setScreen(
            new MappingEditorScreen(this, fileName, entries, mode)
        );
    }

    /** Called by the editor after a successful save. */
    void onSaved(String fileName, EditorMode mode) {
        setStatus(
            Text.translatable(
                mode.isOverride()
                    ? "gui.attributification.mappings.status.overridden"
                    : "gui.attributification.mappings.status.saved",
                fileName
            ).formatted(Formatting.GREEN)
        );
        callbacks.requestFiles();
    }

    void setStatus(Text text) {
        this.status = text;
        this.statusTimer = 160;
    }

    // ── Render ────────────────────────────────────────────────────────────

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

        if (files.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("gui.attributification.mappings.no_files"),
                width / 2,
                HEADER_HEIGHT + 24,
                0xFFA0A0A0
            );
        }

        if (statusTimer > 0 && status != Text.empty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                status,
                width / 2,
                HEADER_HEIGHT - 14,
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
    public boolean shouldPause() {
        return false;
    }

    // ── File list ─────────────────────────────────────────────────────────

    private class FileListWidget
        extends AlwaysSelectedEntryListWidget<FileEntry> {

        FileListWidget() {
            super(
                MinecraftClient.getInstance(),
                MappingsScreen.this.width,
                MappingsScreen.this.height,
                HEADER_HEIGHT,
                MappingsScreen.this.height - FOOTER_HEIGHT,
                24
            );
        }

        @Override
        public int getRowWidth() {
            return Math.min(320, MappingsScreen.this.width - 80);
        }

        protected int getScrollbarPositionX() {
            return width / 2 + getRowWidth() / 2 + 10;
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

    private class FileEntry extends AlwaysSelectedEntryListWidget.Entry<FileEntry> {

        private final MappingFileEntry file;
        private long lastClickTime;

        FileEntry(MappingFileEntry file) {
            this.file = file;
        }

        @Override
        public Text getNarration() {
            return Text.literal(file.fileName());
        }

        @Override
        public void render(
            DrawContext context,
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
            var textRenderer = MappingsScreen.this.textRenderer;
            int textY = y + (entryHeight - 9) / 2;

            // Right-hand badge first, so the name can be clipped against it.
            Text badge = file.editable()
                ? Text.translatable("gui.attributification.mappings.world")
                : Text.translatable("gui.attributification.mappings.read_only");
            int badgeWidth = textRenderer.getWidth(badge);
            context.drawTextWithShadow(
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
            int nsX = context.drawTextWithShadow(
                textRenderer,
                Text.literal(namespace).formatted(Formatting.DARK_GRAY),
                x + 4,
                textY,
                0xFF555555
            );
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(name),
                nsX,
                textY,
                file.editable() ? 0xFFFFFFFF : 0xFFA0A0A0
            );
            context.disableScissor();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            MappingsScreen.this.fileList.setSelected(this);
            long now = Util.getMeasuringTimeMs();
            if (now - lastClickTime < 250L) {
                MappingsScreen.this.openSelected();
            }
            lastClickTime = now;
            return true;
        }
    }
}
