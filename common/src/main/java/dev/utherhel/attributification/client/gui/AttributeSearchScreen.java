package dev.utherhel.attributification.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(
        this,
        58,
        40
    );

    private EditBox searchField;
    private ResultList results;
    private Button selectButton;

    AttributeSearchScreen(
        Screen parent,
        List<String> attributes,
        Consumer<String> onSelect
    ) {
        super(
            Component.translatable("gui.attributification.mappings.attribute_search")
        );
        this.parent = parent;
        this.onSelect = onSelect;
        this.allAttributes = new ArrayList<>(attributes);
        this.allAttributes.sort(Comparator.naturalOrder());
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        searchField = new EditBox(
            font,
            0,
            0,
            220,
            20,
            Component.translatable("gui.attributification.mappings.attribute_search")
        );
        searchField.setMaxLength(256);
        searchField.setHint(
            Component.translatable(
                "gui.attributification.mappings.attribute_search"
            ).withStyle(ChatFormatting.DARK_GRAY)
        );
        searchField.setResponder(query -> results.filter(query));

        results = layout.addToContents(new ResultList());

        LinearLayout footer = layout.addToFooter(
            LinearLayout.horizontal().spacing(8)
        );
        selectButton = footer.addChild(
            Button.builder(
                Component.translatable("gui.attributification.mappings.select"),
                b -> confirmSelection()
            )
                .width(100)
                .build()
        );
        footer.addChild(
            Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .width(100)
                .build()
        );

        layout.visitWidgets(this::addRenderableWidget);
        // The search field sits in the header area, positioned manually below
        // the title so it never overlaps the heading text.
        addRenderableWidget(searchField);

        refreshPositions();
        results.filter(searchField.getValue());
        setInitialFocus(searchField);

        if (allAttributes.isEmpty()) {
            MappingsScreen.requestAttributesIfMissing();
        }
    }

    @Override
    protected void repositionElements() {
        refreshPositions();
    }

    private void refreshPositions() {
        layout.arrangeElements();
        results.updateSize(width, layout);
        searchField.setPosition(width / 2 - 110, 30);
        refreshButtons();
    }

    private void refreshButtons() {
        if (selectButton != null) {
            selectButton.active = results != null &&
            results.getSelected() != null;
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    private void confirmSelection() {
        ResultEntry entry = results.getSelected();
        if (entry == null) return;
        onSelect.accept(entry.id);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter confirms the highlighted result.
        if ((keyCode == 257 || keyCode == 335) && results.getSelected() != null) {
            confirmSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        if (allAttributes.isEmpty()) {
            ctx.drawCenteredString(
                font,
                Component.translatable(
                    "gui.attributification.mappings.status.no_attributes"
                ),
                width / 2,
                layout.getHeaderHeight() + 20,
                0xFFFF5555
            );
        } else if (results.getItemCount() == 0) {
            ctx.drawCenteredString(
                font,
                Component.translatable(
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
        extends ObjectSelectionList<ResultEntry> {

        ResultList() {
            super(
                Minecraft.getInstance(),
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
        public int getItemCount() {
            return super.getItemCount();
        }

        @Override
        public void setSelected(ResultEntry entry) {
            super.setSelected(entry);
            AttributeSearchScreen.this.refreshButtons();
        }
    }

    private class ResultEntry
        extends ObjectSelectionList.Entry<ResultEntry> {

        private final String id;
        private long lastClickTime;

        ResultEntry(String id) {
            this.id = id;
        }

        @Override
        public Component getNarration() {
            return Component.literal(id);
        }

        @Override
        public void render(
            GuiGraphics ctx,
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
            ctx.drawString(
                font,
                Component.literal(id),
                x + 3,
                y + (entryHeight - 9) / 2,
                hovered ? 0xFFFFFFA0 : 0xFFFFFFFF
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            results.setSelected(this);
            long now = Util.getMillis();
            if (now - lastClickTime < 250L) {
                confirmSelection();
            }
            lastClickTime = now;
            return true;
        }
    }
}
