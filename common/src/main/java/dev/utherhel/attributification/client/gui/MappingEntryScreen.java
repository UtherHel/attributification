package dev.utherhel.attributification.client.gui;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Detailed editor for a single {@link EditableMapping}.
 * <p>
 * Every control lives inside an {@link ElementListWidget} row, which gives us
 * vanilla scrolling, clipping and — crucially — correct focus routing, so text
 * fields stay focused while typing.  Text fields write straight into the model
 * on change; the row list is only rebuilt on explicit add/remove presses.
 */
@Environment(EnvType.CLIENT)
class MappingEntryScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int WIDGET_H = 20;
    /** Gap between the label column and the widget column. */
    private static final int LABEL_GAP = 8;
    private static final int SEARCH_W = 22;
    private static final int REMOVE_W = 22;

    private final Screen parent;
    private final EditableMapping mapping;
    private final boolean editable;

    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(
        this,
        33,
        40
    );

    private OptionListWidget list;

    MappingEntryScreen(
        Screen parent,
        EditableMapping mapping,
        boolean editable
    ) {
        super(
            Text.translatable(
                editable
                    ? "gui.attributification.mappings.entry_edit"
                    : "gui.attributification.mappings.entry_view"
            )
        );
        this.parent = parent;
        this.mapping = mapping;
        this.editable = editable;
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        layout.addHeader(title, textRenderer);

        list = layout.addBody(new OptionListWidget());

        DirectionalLayoutWidget footer = layout.addFooter(
            DirectionalLayoutWidget.horizontal().spacing(8)
        );
        footer.add(
            ButtonWidget.builder(ScreenTexts.DONE, b -> close()).width(80).build()
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
        list.position(width, layout);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Widest label text, so the widget column never overlaps a label. */
    private int labelColumnWidth() {
        int max = 0;
        for (String key : new String[] {
            "gui.attributification.mappings.equivalence",
            "gui.attributification.mappings.conversion_equivalence",
            "gui.attributification.mappings.subtract_value",
            "gui.attributification.mappings.mode",
            "gui.attributification.mappings.operation_conversion",
        }) {
            max = Math.max(max, textRenderer.getWidth(Text.translatable(key)));
        }
        return max;
    }

    // ── The list ──────────────────────────────────────────────────────────

    private class OptionListWidget extends ElementListWidget<Row> {

        OptionListWidget() {
            super(
                MinecraftClient.getInstance(),
                MappingEntryScreen.this.width,
                MappingEntryScreen.this.layout.getContentHeight(),
                MappingEntryScreen.this.layout.getHeaderHeight(),
                ROW_H
            );
            rebuild();
        }

        @Override
        public int getRowWidth() {
            return Math.min(400, MappingEntryScreen.this.width - 80);
        }

        /**
         * Recreates all rows from the model.  Called on add/remove only —
         * never while typing — so focus is never yanked out of a field.
         */
        void rebuild() {
            double scroll = getScrollAmount();
            clearEntries();

            // ── Sources ───────────────────────────────────────────────────
            addEntry(
                new SectionRow(
                    Text.translatable("gui.attributification.mappings.source")
                )
            );
            for (int i = 0; i < mapping.sources.size(); i++) {
                addEntry(new IdRow(mapping.sources, i));
            }
            addEntry(
                new AddRow("gui.attributification.mappings.add_source", () -> {
                    mapping.sources.add("");
                    rebuild();
                })
            );

            // ── Targets ───────────────────────────────────────────────────
            addEntry(
                new SectionRow(
                    Text.translatable("gui.attributification.mappings.target")
                )
            );
            for (int i = 0; i < mapping.targets.size(); i++) {
                addEntry(new IdRow(mapping.targets, i));
            }
            addEntry(
                new AddRow("gui.attributification.mappings.add_target", () -> {
                    mapping.targets.add("");
                    rebuild();
                })
            );

            // ── Exclude ───────────────────────────────────────────────────
            addEntry(
                new SectionRow(
                    Text.translatable("gui.attributification.mappings.exclude")
                )
            );
            for (int i = 0; i < mapping.exclude.size(); i++) {
                addEntry(new IdRow(mapping.exclude, i));
            }
            addEntry(
                new AddRow("gui.attributification.mappings.add_exclude", () -> {
                    mapping.exclude.add("");
                    rebuild();
                })
            );

            // ── Numbers & modes ───────────────────────────────────────────
            addEntry(
                new SectionRow(
                    Text.translatable("gui.attributification.mappings.tuning")
                )
            );
            addEntry(
                new NumberRow(
                    "gui.attributification.mappings.equivalence",
                    mapping.equivalence,
                    v -> mapping.equivalence = v
                )
            );
            addEntry(
                new NumberRow(
                    "gui.attributification.mappings.conversion_equivalence",
                    mapping.conversionEquivalence,
                    v -> mapping.conversionEquivalence = v
                )
            );
            addEntry(
                new NumberRow(
                    "gui.attributification.mappings.subtract_value",
                    mapping.subtractValue,
                    v -> mapping.subtractValue = v
                )
            );
            addEntry(
                new WidgetRow(
                    "gui.attributification.mappings.mode",
                    width ->
                        CyclingButtonWidget.<EditableMapping.Mode>builder(
                            EditableMapping.Mode::label
                        )
                            .values(EditableMapping.Mode.values())
                            .initially(mapping.mode)
                            .omitKeyText()
                            .tooltip(mode -> Tooltip.of(mode.description()))
                            .build(
                                0,
                                0,
                                width,
                                WIDGET_H,
                                Text.translatable(
                                    "gui.attributification.mappings.mode"
                                ),
                                (btn, value) -> mapping.mode = value
                            )
                )
            );
            addEntry(
                new WidgetRow(
                    "gui.attributification.mappings.operation_conversion",
                    width ->
                        CyclingButtonWidget.<EditableMapping.OpConversion>builder(
                            EditableMapping.OpConversion::label
                        )
                            .values(EditableMapping.OpConversion.values())
                            .initially(mapping.operationConversion)
                            .omitKeyText()
                            .build(
                                0,
                                0,
                                width,
                                WIDGET_H,
                                Text.translatable(
                                    "gui.attributification.mappings.operation_conversion"
                                ),
                                (btn, value) ->
                                    mapping.operationConversion = value
                            )
                )
            );

            setScrollAmount(scroll);
        }
    }

    // ── Rows ──────────────────────────────────────────────────────────────

    private abstract static class Row extends ElementListWidget.Entry<Row> {}

    /** A non-interactive section heading. */
    private class SectionRow extends Row {

        private final Text label;

        SectionRow(Text label) {
            this.label = label.copy().formatted(Formatting.YELLOW);
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
            // Sits at the bottom of its row so it reads as a header for what follows.
            ctx.drawTextWithShadow(
                textRenderer,
                label,
                x,
                y + entryHeight - 11,
                0xFFFFFFFF
            );
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(
                new Selectable() {
                    @Override
                    public Selectable.SelectionType getType() {
                        return Selectable.SelectionType.HOVERED;
                    }

                    @Override
                    public void appendNarrations(
                        NarrationMessageBuilder builder
                    ) {
                        builder.put(NarrationPart.TITLE, label);
                    }
                }
            );
        }
    }

    /**
     * One attribute ID: a text field, a search button, and a remove button.
     * The field writes into {@code list[index]} directly — no rebuild — so
     * typing never loses focus.
     */
    private class IdRow extends Row {

        private final List<String> backing;
        private final int index;
        private final TextFieldWidget field;
        private final ButtonWidget searchBtn;
        private final ButtonWidget removeBtn;

        IdRow(List<String> backing, int index) {
            this.backing = backing;
            this.index = index;

            field = new TextFieldWidget(
                textRenderer,
                0,
                0,
                100,
                WIDGET_H,
                Text.translatable("gui.attributification.mappings.attribute_id")
            );
            field.setMaxLength(256);
            field.setText(backing.get(index));
            field.setEditable(editable);
            field.setPlaceholder(
                Text.translatable(
                    "gui.attributification.mappings.attribute_id"
                ).formatted(Formatting.DARK_GRAY)
            );
            field.setChangedListener(value -> {
                if (index < backing.size()) backing.set(index, value.trim());
            });

            searchBtn = ButtonWidget.builder(
                Text.literal("…"),
                b ->
                    client.setScreen(
                        new AttributeSearchScreen(
                            MappingEntryScreen.this,
                            MappingsScreen.attributes(),
                            selected -> {
                                if (index < backing.size()) {
                                    backing.set(index, selected);
                                }
                                field.setText(selected);
                            }
                        )
                    )
            )
                .width(SEARCH_W)
                .tooltip(
                    Tooltip.of(
                        Text.translatable(
                            "gui.attributification.mappings.search_tooltip"
                        )
                    )
                )
                .build();
            searchBtn.active = editable;

            removeBtn = ButtonWidget.builder(
                Text.literal("✕").formatted(Formatting.RED),
                b -> {
                    if (index < backing.size()) {
                        backing.remove(index);
                        list.rebuild();
                    }
                }
            )
                .width(REMOVE_W)
                .tooltip(
                    Tooltip.of(
                        Text.translatable(
                            "gui.attributification.mappings.remove_mapping"
                        )
                    )
                )
                .build();
            removeBtn.active = editable;
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
            int top = y + (entryHeight - WIDGET_H) / 2;
            int right = x + entryWidth;

            removeBtn.setPosition(right - REMOVE_W, top);
            searchBtn.setPosition(right - REMOVE_W - 4 - SEARCH_W, top);

            int fieldW = Math.max(
                40,
                entryWidth - REMOVE_W - SEARCH_W - 8
            );
            field.setPosition(x, top);
            field.setWidth(fieldW);

            field.render(ctx, mouseX, mouseY, delta);
            searchBtn.render(ctx, mouseX, mouseY, delta);
            removeBtn.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(field, searchBtn, removeBtn);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(field, searchBtn, removeBtn);
        }
    }

    /** A single full-width "add another" button. */
    private class AddRow extends Row {

        private final ButtonWidget button;

        AddRow(String labelKey, Runnable action) {
            button = ButtonWidget.builder(
                Text.translatable(labelKey),
                b -> action.run()
            ).build();
            button.active = editable;
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
            button.setPosition(x, y + (entryHeight - WIDGET_H) / 2);
            button.setWidth(entryWidth);
            button.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(button);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(button);
        }
    }

    /**
     * Label on the left, widget on the right.  The widget starts after the
     * widest label in the screen, so text and controls never overlap.
     */
    private class WidgetRow extends Row {

        private final Text label;
        private final net.minecraft.client.gui.widget.ClickableWidget widget;

        WidgetRow(
            String labelKey,
            java.util.function.IntFunction<
                net.minecraft.client.gui.widget.ClickableWidget
            > factory
        ) {
            this.label = Text.translatable(labelKey);
            this.widget = factory.apply(120);
        }

        /** Width available to the control once the label column is reserved. */
        private int widgetWidth(int entryWidth) {
            return Math.max(60, entryWidth - labelColumnWidth() - LABEL_GAP);
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
            int w = widgetWidth(entryWidth);
            int top = y + (entryHeight - WIDGET_H) / 2;

            ctx.drawTextWithShadow(
                textRenderer,
                label,
                x,
                y + (entryHeight - 9) / 2,
                0xFFCCCCCC
            );

            widget.setPosition(x + entryWidth - w, top);
            widget.setWidth(w);
            widget.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(widget);
        }
    }

    /** A {@link WidgetRow} holding a validating numeric text field. */
    private class NumberRow extends Row {

        private final Text label;
        private final TextFieldWidget field;

        NumberRow(String labelKey, double initial, Consumer<Double> setter) {
            this.label = Text.translatable(labelKey);
            field = new TextFieldWidget(
                textRenderer,
                0,
                0,
                80,
                WIDGET_H,
                Text.translatable(labelKey)
            );
            field.setMaxLength(32);
            field.setText(EditableMapping.format(initial));
            field.setEditable(editable);
            field.setChangedListener(value -> {
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    setter.accept(0.0);
                    field.setEditableColor(0xFFFFFF);
                    return;
                }
                try {
                    setter.accept(Double.parseDouble(trimmed));
                    field.setEditableColor(0xFFFFFF);
                } catch (NumberFormatException ignored) {
                    field.setEditableColor(0xFF5555);
                }
            });
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
            int w = Math.max(60, entryWidth - labelColumnWidth() - LABEL_GAP);
            int top = y + (entryHeight - WIDGET_H) / 2;

            ctx.drawTextWithShadow(
                textRenderer,
                label,
                x,
                y + (entryHeight - 9) / 2,
                0xFFCCCCCC
            );

            field.setPosition(x + entryWidth - w, top);
            field.setWidth(w);
            field.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(field);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(field);
        }
    }
}
