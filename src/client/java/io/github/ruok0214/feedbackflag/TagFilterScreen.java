package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

final class TagFilterScreen extends FlagScreen {
    private final List<String> tags;
    private final Consumer<String> select;
    private final List<Button> choices = new ArrayList<>();
    private List<String> matches = List.of();
    private String query = "";
    private int page, left, panelWidth, count;
    private Button previous, next;
    TagFilterScreen(Screen parent, List<String> tags, Consumer<String> select) {
        super("tags_title", parent); this.tags = List.copyOf(tags); this.select = select;
    }
    @Override protected void init() {
        choices.clear(); panelWidth = Math.min(440, width - 24); left = (width - panelWidth) / 2;
        int half = (panelWidth - 6) / 2;
        button(tr("all_tags"), left, 31, half, () -> choose(null));
        button(tr("untagged"), left + half + 6, 31, panelWidth - half - 6, () -> choose(""));
        var search = addRenderableWidget(new EditBox(font, left, 61, panelWidth, 20, tr("tag_search")));
        search.setHint(tr("tag_search")); search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        count = Math.max(1, (height - 127) / 24);
        for (int i = 0; i < count; i++) {
            final int row = i;
            choices.add(button(Component.empty(), left, 92 + i * 24, panelWidth, () -> {
                int index = page * count + row; if (index < matches.size()) choose(matches.get(index));
            }));
        }
        previous = button(Component.literal("<"), left, height - 29, 26, () -> { page--; refresh(); });
        next = button(Component.literal(">"), left + 30, height - 29, 26, () -> { page++; refresh(); });
        button(tr("back"), left + panelWidth - 90, height - 29, 90, this::onClose);
        refresh();
    }
    private void choose(String tag) { select.accept(tag); onClose(); }
    private void refresh() {
        matches = tags.stream().filter(t -> t.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).toList();
        page = Math.clamp(page, 0, Math.max(0, (matches.size() - 1) / count));
        for (int i = 0; i < choices.size(); i++) {
            int index = page * count + i; var choice = choices.get(i); choice.visible = index < matches.size();
            if (choice.visible) choice.setMessage(Component.literal(matches.get(index)));
        }
        previous.active = page > 0; next.active = (page + 1) * count < matches.size();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        page += vertical < 0 ? 1 : -1; refresh(); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        if (matches.isEmpty()) text(g, tr("no_tags"), left, 96, panelWidth, MUTED);
        text(g, Component.literal((page + 1) + "/" + Math.max(1, (matches.size() + count - 1) / count)), left + 66, height - 23, panelWidth - 162, MUTED);
    }
}
