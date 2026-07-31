package dev.utherhel.attributification.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

/**
 * Searchable picker for a registered attribute ID.
 * <p>
 * Always returns to {@code parent} — both on pick and on cancel — instead of
 * clearing the screen stack.
 */
@Environment(EnvType.CLIENT)
class AttributeSearchScreen extends Screen {

    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final List<String> allAttributes;

    private TextFieldWidget searchField;
    private ResultList results;
    private ButtonWidget selectButton;
    private ButtonWidget cancelButton;

    AttributeSearchScreen(
        Screen parent,
        List<String> attributes,
        Consumer<String> onSelect
    ) {
        super(
            Text.translatable("gui.attributification.mappings.attribute_search")
        );
        this.parent = parent;
        this.onSelect = onSelect;
        this.allAttributes = new ArrayList<>(attributes);
        this.allAttributes.sort(Comparator.naturalOrder());
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        results = new ResultList();

        searchField = new TextFieldWidget(
            textRenderer,
            0,
            0,
            220,
            20,
            Text.translatable("gui.attributification.mappings.attribute_search")
        );
        searchField.setMaxLength(256);
        searchField.setPlaceholder(
            Text.translatable(
                "gui.attributification.mappings.attribute_search"
            ).formatted(Formatting.DARK_GRAY)
        );
        searchField.setChangedListener(query -> results.filter(query));

        selectButton = ButtonWidget.builder(
            Text.translatable("gui.attributification.mappings.select"),
            b -> confirmSelection()
        ).dimensions(0, 0, 100, 20).build();

        cancelButton = ButtonWidget.builder(
            ScreenTexts.CANCEL, b -> close()
        ).dimensions(0, 0, 100, 20).build();

        addDrawableChild(results);
        addDrawableChild(searchField);
        addDrawableChild(selectButton);
        addDrawableChild(cancelButton);

        refreshPositions();
        results.filter(searchField.getText());
        setInitialFocus(searchField);

        if (allAttributes.isEmpty()) {
            MappingsScreen.requestAttributesIfMissing();
        }
    }

    @Override
    protected void initTabNavigation() {
        // 1.20.1 calls this on every return to an already-initialized screen
        // and on resize, without clearing children first — so re-init would
        // duplicate every widget. Only repositioning is needed.
        refreshPositions();
    }

    /** Applies the header/footer/list layout for the current screen size. */
    private void refreshPositions() {
        results.updateSize(width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT);
        searchField.setPosition(width / 2 - 110, 30);
        selectButton.setPosition((width - 208) / 2, height - FOOTER_HEIGHT + 10);
        cancelButton.setPosition(
            (width - 208) / 2 + 108,
            height - FOOTER_HEIGHT + 10
        );
    }

    private void refreshButtons() {
        if (selectButton != null) {
            selectButton.active = results != null &&
                results.getSelectedOrNull() != null;
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    private void confirmSelection() {
        ResultEntry entry = results.getSelectedOrNull();
        if (entry == null) return;
        onSelect.accept(entry.id);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter confirms the highlighted result.
        if ((keyCode == 257 || keyCode == 335) && results.getSelectedOrNull() != null) {
            confirmSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // The title is drawn below the search field, which sits in the header
        // area on top of it (matching the reference draw order).
        ctx.drawCenteredTextWithShadow(
            textRenderer,
            title,
            width / 2,
            25,
            0xFFFFFF
        );

        super.render(ctx, mouseX, mouseY, delta);

        if (allAttributes.isEmpty()) {
            ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(
                    "gui.attributification.mappings.status.no_attributes"
                ),
                width / 2,
                HEADER_HEIGHT + 20,
                0xFFFF5555
            );
        } else if (results.getEntryCount() == 0) {
            ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(
                    "gui.attributification.mappings.no_matches"
                ),
                width / 2,
                HEADER_HEIGHT + 20,
                0xFFA0A0A0
            );
        }
    }

    // ── Result list ───────────────────────────────────────────────────────

    private class ResultList
        extends AlwaysSelectedEntryListWidget<ResultEntry> {

        ResultList() {
            super(
                MinecraftClient.getInstance(),
                AttributeSearchScreen.this.width,
                AttributeSearchScreen.this.height,
                HEADER_HEIGHT,
                AttributeSearchScreen.this.height - FOOTER_HEIGHT,
                16
            );
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, AttributeSearchScreen.this.width - 60);
        }

        protected int getScrollbarPositionX() {
            return width / 2 + getRowWidth() / 2 + 10;
        }

        /** Case-insensitive substring match; exact prefixes rank first. */
        void filter(String query) {
            clearEntries();
            setSelected(null);

            String needle = query == null ? "" : query.trim().toLowerCase();
            List<String> prefix = new ArrayList<>();
            List<String> contains = new ArrayList<>();

            for (String id : allAttributes) {
                String lower = id.toLowerCase();
                if (needle.isEmpty() || lower.startsWith(needle)) {
                    prefix.add(id);
                } else if (lower.contains(needle)) {
                    contains.add(id);
                }
            }

            for (String id : prefix) addEntry(new ResultEntry(id));
            for (String id : contains) addEntry(new ResultEntry(id));

            setScrollAmount(0.0);
            refreshButtons();
        }

        @Override
        public int getEntryCount() {
            return super.getEntryCount();
        }

        @Override
        public void setSelected(ResultEntry entry) {
            super.setSelected(entry);
            AttributeSearchScreen.this.refreshButtons();
        }
    }

    private class ResultEntry
        extends AlwaysSelectedEntryListWidget.Entry<ResultEntry> {

        private final String id;
        private long lastClickTime;

        ResultEntry(String id) {
            this.id = id;
        }

        @Override
        public Text getNarration() {
            return Text.literal(id);
        }

        @Override
        public void render(
            DrawContext ctx,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float delta
        ) {
            ctx.drawTextWithShadow(
                textRenderer,
                Text.literal(id),
                x + 3,
                y + (entryHeight - 9) / 2,
                hovered ? 0xFFFFFFA0 : 0xFFFFFFFF
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            results.setSelected(this);
            long now = Util.getMeasuringTimeMs();
            if (now - lastClickTime < 250L) {
                confirmSelection();
            }
            lastClickTime = now;
            return true;
        }
    }
}
