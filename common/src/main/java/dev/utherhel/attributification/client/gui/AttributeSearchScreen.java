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
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
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

    private final Screen parent;
    private final Consumer<String> onSelect;
    private final List<String> allAttributes;

    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(
        this,
        58,
        40
    );

    private TextFieldWidget searchField;
    private ResultList results;
    private ButtonWidget selectButton;

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
        layout.addHeader(title, textRenderer);

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

        results = layout.addBody(new ResultList());

        DirectionalLayoutWidget footer = layout.addFooter(
            DirectionalLayoutWidget.horizontal().spacing(8)
        );
        selectButton = footer.add(
            ButtonWidget.builder(
                Text.translatable("gui.attributification.mappings.select"),
                b -> confirmSelection()
            )
                .width(100)
                .build()
        );
        footer.add(
            ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .width(100)
                .build()
        );

        layout.forEachChild(this::addDrawableChild);
        // The search field sits in the header area, positioned manually below
        // the title so it never overlaps the heading text.
        addDrawableChild(searchField);

        refreshPositions();
        results.filter(searchField.getText());
        setInitialFocus(searchField);

        if (allAttributes.isEmpty()) {
            MappingsScreen.requestAttributesIfMissing();
        }
    }

    @Override
    protected void initTabNavigation() {
        refreshPositions();
    }

    private void refreshPositions() {
        layout.refreshPositions();
        results.position(width, layout);
        searchField.setPosition(width / 2 - 110, 30);
        refreshButtons();
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
        super.render(ctx, mouseX, mouseY, delta);

        if (allAttributes.isEmpty()) {
            ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(
                    "gui.attributification.mappings.status.no_attributes"
                ),
                width / 2,
                layout.getHeaderHeight() + 20,
                0xFFFF5555
            );
        } else if (results.getEntryCount() == 0) {
            ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(
                    "gui.attributification.mappings.no_matches"
                ),
                width / 2,
                layout.getHeaderHeight() + 20,
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
                AttributeSearchScreen.this.layout.getContentHeight(),
                AttributeSearchScreen.this.layout.getHeaderHeight(),
                16
            );
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, AttributeSearchScreen.this.width - 60);
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
