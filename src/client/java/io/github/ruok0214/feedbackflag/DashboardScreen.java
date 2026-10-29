package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class DashboardScreen extends FlagScreen {
    private final FlagManager manager;
    private final UUID filter;
    private String tagFilter;
    private int flagPage, rowOffset, leftWidth, rightX, rightWidth, flagRows, eventRows;
    private List<FlagBook.Flag> flags = List.of();
    private List<TickGroup> groups = List.of(), visibleGroups = List.of();
    private record Row(TickGroup group, FlagBook.Event event) {
        String key() { return event == null ? "g" + group.id() : "e" + event.sequence(); }
    }
    private List<Row> rows = List.of();
    private final Set<Long> expanded = new HashSet<>();
    private final List<Button> flagButtons = new ArrayList<>(), eventButtons = new ArrayList<>();
    private Button flagPrevious, flagNext, eventPrevious, eventNext, clearHistory, tagButton;
    private TimelineScale scale = TimelineScale.of(List.of());
    private int clearWidth;
    private long revision = -1;

    public DashboardScreen(FlagManager manager, UUID filter) {
        super("title", null); this.manager = manager; this.filter = filter;
    }
    @Override protected void init() {
        flagButtons.clear(); eventButtons.clear();
        leftWidth = Math.max(104, (width - 30) / 3);
        rightX = leftWidth + 20; rightWidth = width - rightX - 10;
        int actionWidth = (width - 28) / 3;
        addRenderableWidget(Button.builder(tr("global", state(manager.book().enabled())), b -> {
            manager.book().setEnabled(!manager.book().enabled()); manager.save();
            b.setMessage(tr("global", state(manager.book().enabled())));
        }).bounds(10, 29, actionWidth, 20).build());
        button(tr("place"), 14 + actionWidth, 29, actionWidth, manager::startPlacing);
        button(tr("keys"), 18 + 2 * actionWidth, 29, actionWidth,
                () -> minecraft.gui.setScreen(new KeySettingsScreen(this)));
        addRenderableWidget(new AbstractSliderButton(10, 54, leftWidth, 20, Component.empty(), manager.book().transparency() / 100d) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(tr("transparency", (int)Math.round(value * 100))); }
            @Override protected void applyValue() { manager.book().setTransparency((int)Math.round(value * 100)); }
        });
        var display = addRenderableWidget(Button.builder(displayLabel(), b -> {
            manager.book().setDisplayMode(manager.book().displayMode().next()); manager.save(); b.setMessage(displayLabel());
        }).bounds(10, 80, leftWidth, 20).build());
        display.setTooltip(Tooltip.create(tr("display_hint")));
        tagButton = button(tagLabel(), rightX, 54, rightWidth, () -> minecraft.gui.setScreen(
                new TagFilterScreen(this, manager.book().availableTags(), selected -> {
                    tagFilter = selected; rowOffset = 0; expanded.clear();
                })));
        clearWidth = Math.min(130, rightWidth / 2);
        clearHistory = button(tr(filter == null ? "clear_history" : "clear_flag_history"),
                rightX + rightWidth - clearWidth, 80, clearWidth, () -> minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) { manager.book().clearHistory(filter); manager.save(); rowOffset = 0; expanded.clear(); }
                    minecraft.gui.setScreen(this);
                }, tr(filter == null ? "clear_history_title" : "clear_flag_history_title"), tr("clear_history_hint"))));
        clearHistory.setTooltip(Tooltip.create(tr("clear_filter_hint")));
        flagRows = Math.max(1, (height - 151) / 27);
        eventRows = Math.max(1, (height - 184) / 26);
        for (int i = 0; i < flagRows; i++) {
            final int row = i;
            flagButtons.add(button(Component.empty(), 10, 124 + i * 27, leftWidth, () -> {
                int index = flagPage * flagRows + row;
                if (index < flags.size()) minecraft.gui.setScreen(new FlagEditScreen(this, manager, flags.get(index).id()));
            }));
        }
        for (int i = 0; i < eventRows; i++) eventButtons.add(addRenderableWidget(new TimelineButton(i)));
        flagPrevious = button(Component.literal("<"), 10, height - 29, 22, () -> { flagPage--; refresh(false); });
        flagNext = button(Component.literal(">"), 36, height - 29, 22, () -> { flagPage++; refresh(false); });
        eventPrevious = button(Component.literal("<"), rightX, height - 29, 22, () -> { rowOffset -= eventRows; refresh(false); });
        eventNext = button(Component.literal(">"), rightX + 26, height - 29, 22, () -> { rowOffset += eventRows; refresh(false); });
        button(tr(filter == null ? "close" : "all_flags"), width - 88, height - 29, 78, () -> {
            if (filter == null) onClose(); else minecraft.gui.setScreen(new DashboardScreen(manager, null));
        });
        revision = -1; refresh(false);
    }
    private Component displayLabel() { return tr("display", tr("display." + manager.book().displayMode().name().toLowerCase(Locale.ROOT))); }
    private Component tagLabel() { return tr("tag_filter", tagFilter == null ? tr("all_tags").getString() : tagFilter.isEmpty() ? tr("untagged").getString() : tagFilter); }
    @Override public void tick() {
        if (manager.book() == null) { onClose(); return; }
        if (revision != manager.book().revision()) refresh(true);
    }
    private void refresh(boolean keepAnchor) {
        String anchor = keepAnchor && rowOffset > 0 && rowOffset < rows.size() ? rows.get(rowOffset).key() : null;
        flags = manager.book().flags(); groups = TickGroup.collect(manager.book(), filter, tagFilter);
        Set<Long> existing = new HashSet<>(); groups.forEach(g -> existing.add(g.id())); expanded.retainAll(existing);
        List<Row> next = new ArrayList<>();
        for (var group : groups) {
            next.add(new Row(group, null));
            if (expanded.contains(group.id())) for (var event : group.events()) next.add(new Row(group, event));
        }
        rows = List.copyOf(next);
        if (anchor != null) for (int i = 0; i < rows.size(); i++) if (rows.get(i).key().equals(anchor)) { rowOffset = i; break; }
        revision = manager.book().revision();
        flagPage = Math.clamp(flagPage, 0, Math.max(0, (flags.size() - 1) / flagRows));
        rowOffset = Math.clamp(rowOffset, 0, Math.max(0, rows.size() - eventRows));
        var pageGroups = new LinkedHashMap<Long, TickGroup>();
        for (int i = 0; i < eventRows; i++) { var row = rowAt(i); if (row != null) pageGroups.putIfAbsent(row.group().id(), row.group()); }
        visibleGroups = List.copyOf(pageGroups.values());
        scale = TimelineScale.of(visibleGroups.stream().map(TickGroup::representative).toList());
        tagButton.setMessage(tagLabel());
        clearHistory.active = manager.book().events().stream().anyMatch(e -> filter == null || filter.equals(e.flagId()))
                || flags.stream().anyMatch(f -> (filter == null || filter.equals(f.id())) && f.total() != 0);
        for (int i = 0; i < flagButtons.size(); i++) {
            int index = flagPage * flagRows + i; var b = flagButtons.get(i); b.visible = index < flags.size();
            if (!b.visible) continue;
            var f = flags.get(index);
            b.setMessage(Component.literal(fit((f.text().isBlank() ? "Flag" : f.text()) + " · " + f.total(), leftWidth - 14)).withColor(f.color()));
            b.setTooltip(Tooltip.create(tr("flag_summary", f.target().coordinates(), tr(manager.status(f)), f.total())
                    .copy().append("\n").append(tr("event_tags", f.tags().isEmpty() ? tr("untagged").getString() : String.join(", ", f.tags())))));
        }
        for (int i = 0; i < eventButtons.size(); i++) {
            var row = rowAt(i); var b = eventButtons.get(i); b.visible = row != null;
            if (row == null) continue;
            b.setMessage(rowLabel(row));
            b.setTooltip(Tooltip.create(row.event() == null ? tr("group_hint")
                    : tr("event_hint", tr("kind." + row.event().kind().name().toLowerCase(Locale.ROOT)), row.event().target().coordinates())));
        }
        flagPrevious.active = flagPage > 0; flagNext.active = (flagPage + 1) * flagRows < flags.size();
        eventPrevious.active = rowOffset > 0; eventNext.active = rowOffset + eventRows < rows.size();
    }
    private Row rowAt(int row) { int index = rowOffset + row; return index >= 0 && index < rows.size() ? rows.get(index) : null; }
    private Component tickLabel(FlagBook.Event event) { return tr(event.worldTick() ? "ticks" : "legacy_ticks", manager.book().relativeTick(event)); }
    private Component groupLabel(TickGroup group) {
        var text = tickLabel(group.representative()).copy();
        if (group.flags().size() > 1) text.append(" · ").append(tr("group_more", group.flags().size() - 1));
        return text.append(" · ").append(tr("group_changes", group.events().size()));
    }
    private Component rowLabel(Row row) {
        return row.event() == null ? Component.literal(expanded.contains(row.group().id()) ? "▼ " : "▶ ").append(groupLabel(row.group()))
                : Component.literal("↳ " + row.event().text()).withColor(row.event().color());
    }
    private void activate(int index) {
        var row = rowAt(index); if (row == null) return;
        if (row.event() != null) minecraft.gui.setScreen(new EventScreen(this, manager, row.event()));
        else { if (!expanded.add(row.group().id())) expanded.remove(row.group().id()); refresh(false); }
    }
    private int dotX(TickGroup group) { return rightX + 7 + (int)(scale.position(group.representative()) * (rightWidth - 14)); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 0 && event.y() >= 119 && event.y() <= 138) {
            for (var group : visibleGroups) if (Math.abs(event.x() - dotX(group)) <= 5) {
                if (!expanded.add(group.id())) expanded.remove(group.id());
                for (int i = 0; i < rows.size(); i++) if (rows.get(i).group().id() == group.id()) { rowOffset = i; break; }
                refresh(false); return true;
            }
        }
        return super.mouseClicked(event, twice);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        if (x < rightX) flagPage += vertical < 0 ? 1 : -1;
        else rowOffset += vertical < 0 ? 1 : -1;
        refresh(false); return true;
    }
    @Override public void removed() { manager.save(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.fill(rightX - 6, 104, rightX - 5, height - 35, 0xFF33465B);
        long active = flags.stream().filter(f -> manager.status(f).equals("watching")).count();
        String error = manager.errorKey();
        var summary = error == null ? tr("summary", active, flags.size(), manager.book().events().size()) : tr(error);
        text(g, summary, rightX, 86, rightWidth - clearWidth - 6, error == null ? MUTED : 0xFFFF8888);
        if (x >= rightX && x < rightX + rightWidth - clearWidth && y >= 80 && y < 100) g.setTooltipForNextFrame(summary, x, y);
        text(g, tr("flags"), 10, 109, leftWidth, WHITE);
        text(g, tr(filter == null ? "timeline" : "filtered_timeline"), rightX, 109, rightWidth, WHITE);
        if (flags.isEmpty()) g.textWithWordWrap(font, tr("no_flags"), 12, 128, leftWidth - 4, MUTED);
        else for (int i = 0; i < flagRows; i++) {
            int index = flagPage * flagRows + i; if (index >= flags.size()) break;
            String status = manager.status(flags.get(index));
            g.fill(11, 125 + i * 27, 14, 143 + i * 27, status.equals("watching") ? 0xFF66D9A0 : status.equals("paused") ? 0xFFDAAD60 : 0xFF697586);
        }
        g.horizontalLine(rightX + 5, rightX + rightWidth - 5, 128, 0xFF48647F);
        for (var group : visibleGroups.reversed()) {
            int px = dotX(group); var colors = group.flags();
            for (int i = 0; i < Math.min(4, colors.size()); i++) g.fill(px - 3, 123 + i * 3, px + 4, 126 + i * 3, 0xFF000000 | colors.get(i).color());
            if (Math.abs(x - px) <= 5 && y >= 119 && y <= 138) g.setTooltipForNextFrame(groupLabel(group), x, y);
        }
        if (groups.isEmpty()) text(g, tr(tagFilter == null ? "no_events" : "no_tag_events"), rightX + 2, 143, rightWidth - 4, MUTED);
        else if (!visibleGroups.isEmpty()) {
            text(g, scale.worldTicks() ? Component.literal(tickLabel(visibleGroups.getLast().representative()).getString() + " → " + tickLabel(visibleGroups.getFirst().representative()).getString())
                    : tr("mixed_ticks"), rightX, 143, rightWidth, MUTED);
        }
        text(g, Component.literal((flagPage + 1) + "/" + Math.max(1, (flags.size() + flagRows - 1) / flagRows)), 64, height - 23, leftWidth - 54, MUTED);
        text(g, Component.literal(rows.isEmpty() ? "0/0" : (rowOffset + 1) + "–" + Math.min(rows.size(), rowOffset + eventRows) + "/" + rows.size()), rightX + 54, height - 23, Math.max(1, rightWidth - 138), MUTED);
    }
    private final class TimelineButton extends Button {
        private final int index;
        TimelineButton(int index) {
            super(rightX, 156 + index * 26, rightWidth, 24, Component.empty(), ignored -> activate(index), DEFAULT_NARRATION);
            this.index = index;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int x, int y, float delta) {
            extractDefaultSprite(g);
            var row = rowAt(index); if (row == null) return;
            text(g, getMessage(), getX() + 8, getY() + (row.event() == null ? 4 : 8), getWidth() - 16, WHITE);
            if (row.event() != null) g.fill(getX() + 2, getY() + 4, getX() + 5, getY() + 20, 0xFF000000 | row.event().color());
            else {
                var flags = row.group().flags(); double cell = Math.min(12d, (getWidth() - 16d) / flags.size());
                for (int i = 0; i < flags.size(); i++) {
                    int start = getX() + 8 + (int)(i * cell), end = getX() + 8 + (int)((i + 1) * cell);
                    g.fill(start, getY() + 18, Math.max(start + 1, end - (cell >= 4 ? 2 : 0)), getY() + 21, 0xFF000000 | flags.get(i).color());
                }
            }
        }
    }
}
