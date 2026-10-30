package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

final class FlagSearchScreen extends FlagScreen {
    private final FlagManager manager;
    private String name = "", color = "", tags = "";
    private FlagSearch.Activation activation = FlagSearch.Activation.ALL;
    private final List<Button> results = new ArrayList<>();
    private final List<Button> checks = new ArrayList<>();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private Button bulk;
    private List<FlagBook.Flag> matches = List.of();
    private EditBox colorBox;
    private Button previous, next, activationButton;
    private int page, count, left, panelWidth, half;
    private long revision = -1;

    FlagSearchScreen(Screen parent, FlagManager manager) { super("search_title", parent); this.manager = manager; }

    @Override protected void init() {
        results.clear(); checks.clear(); panelWidth = Math.min(560, width - 24); left = (width - panelWidth) / 2; half = (panelWidth - 8) / 2;
        input(left, 45, "search_name", name, value -> name = value);
        colorBox = input(left + half + 8, 45, "search_color", color, value -> color = value);
        input(left, 86, "search_tags", tags, value -> tags = value).setTooltip(Tooltip.create(tr("search_tags_hint_detail")));
        activationButton = button(activationLabel(), left + half + 8, 86, half, () -> {
            activation = activation.next(); activationButton.setMessage(activationLabel()); page = 0; refresh();
        });
        activationButton.setTooltip(Tooltip.create(tr("search_active_hint")));
        int cell = panelWidth / 16;
        for (int i = 0; i < BasicPalette.COLORS.size(); i++) {
            var swatch = BasicPalette.COLORS.get(i);
            addRenderableWidget(Button.builder(Component.literal("■").withColor(swatch.rgb()),
                    ignored -> colorBox.setValue(String.format(Locale.ROOT, "#%06X", swatch.rgb())))
                    .bounds(left + i * cell, 113, cell - 2, 18)
                    .tooltip(Tooltip.create(swatch.label().copy().append(String.format(Locale.ROOT, " · #%06X", swatch.rgb())))).build());
        }
        int third = (panelWidth - 8) / 3;
        button(tr("bulk.select_results"), left, 150, third, () -> { matches.forEach(f -> selected.add(f.id())); refresh(); });
        button(tr("bulk.clear_selection"), left + third + 4, 150, third, () -> { selected.clear(); refresh(); });
        bulk = button(tr("bulk.open", selected.size()), left + (third + 4) * 2, 150, third,
                () -> minecraft.gui.setScreen(new BulkEditScreen(this, manager, selected)));
        count = Math.max(1, (height - 210) / 24);
        for (int i = 0; i < count; i++) {
            final int row = i;
            checks.add(button(Component.empty(), left, 177 + i * 24, 24, () -> {
                int index = page * count + row;
                if (index < matches.size()) { UUID id = matches.get(index).id(); if (!selected.add(id)) selected.remove(id); refresh(); }
            }));
            results.add(button(Component.empty(), left + 28, 177 + i * 24, panelWidth - 28, () -> {
                int index = page * count + row;
                if (index < matches.size()) minecraft.gui.setScreen(new FlagEditScreen(this, manager, matches.get(index).id()));
            }));
        }
        previous = button(Component.literal("<"), left, height - 29, 24, () -> { page--; refresh(); });
        next = button(Component.literal(">"), left + 28, height - 29, 24, () -> { page++; refresh(); });
        button(tr("search_reset"), left + panelWidth - 156, height - 29, 76, () -> {
            name = color = tags = ""; activation = FlagSearch.Activation.ALL; page = 0; rebuildWidgets();
        });
        button(tr("back"), left + panelWidth - 76, height - 29, 76, this::onClose);
        refresh();
    }
    private EditBox input(int x, int y, String key, String value, Consumer<String> update) {
        var box = addRenderableWidget(new EditBox(font, x, y, half, 20, tr(key)));
        box.setMaxLength(512); box.setHint(tr(key + "_hint")); box.setValue(value);
        box.setResponder(text -> { update.accept(text); page = 0; refresh(); });
        return box;
    }
    private Component activationLabel() { return tr("search_active." + activation.name().toLowerCase(Locale.ROOT)); }
    private void refresh() {
        if (next == null || count == 0) return;
        var query = new FlagSearch(name, color, tags, activation);
        selected.removeIf(id -> manager.book().flag(id) == null);
        bulk.setMessage(tr("bulk.open", selected.size())); bulk.active = !selected.isEmpty();
        matches = manager.book().flags().stream().filter(flag -> query.matches(flag,
                BasicPalette.COLORS.stream().filter(c -> c.rgb() == flag.color())
                        .flatMap(c -> java.util.stream.Stream.of(c.name(), c.name().replace('_', ' '), c.label().getString())).toList())).toList();
        page = Math.clamp(page, 0, Math.max(0, (matches.size() - 1) / count));
        for (int i = 0; i < results.size(); i++) {
            int index = page * count + i; var button = results.get(i); button.visible = index < matches.size();
            checks.get(i).visible = button.visible;
            if (!button.visible) continue;
            var flag = matches.get(index);
            checks.get(i).setMessage(Component.literal(selected.contains(flag.id()) ? "☑" : "□"));
            checks.get(i).setTooltip(Tooltip.create(tr("bulk.select_flag", flag.text())));
            String label = (flag.text().isBlank() ? "Flag" : flag.text()) + " · " + tr(flag.enabled() ? "search_active.enabled" : "search_active.disabled").getString();
            button.setMessage(Component.literal(fit(label, panelWidth - 44)).withColor(flag.color()));
            button.setTooltip(Tooltip.create(Component.literal(flag.text() + String.format(Locale.ROOT, " · #%06X", flag.color()))
                    .append("\n").append(tr("event_tags", flag.tags().isEmpty() ? tr("untagged").getString() : String.join(", ", flag.tags())))
                    .append("\n").append(tr("flag_summary", flag.target().coordinates(), tr(manager.status(flag)), flag.total()))));
        }
        previous.active = page > 0; next.active = (page + 1) * count < matches.size(); revision = manager.book().revision();
    }
    @Override public void tick() {
        if (manager.book() == null) { onClose(); return; }
        if (revision != manager.book().revision()) refresh();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || y < 176) return false;
        page += vertical < 0 ? 1 : -1; refresh(); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        text(g, tr("search_name"), left, 32, half, MUTED);
        text(g, tr("search_color"), left + half + 8, 32, half, MUTED);
        text(g, tr("search_tags"), left, 73, half, MUTED);
        text(g, tr("search_active"), left + half + 8, 73, half, MUTED);
        text(g, tr("search_count", matches.size(), manager.book().flags().size()), left, 138, panelWidth, MUTED);
        if (matches.isEmpty()) text(g, tr("search_empty"), left, 184, panelWidth, MUTED);
        text(g, Component.literal((page + 1) + "/" + Math.max(1, (matches.size() + count - 1) / count)), left + 60, height - 23, panelWidth - 220, MUTED);
    }
}
