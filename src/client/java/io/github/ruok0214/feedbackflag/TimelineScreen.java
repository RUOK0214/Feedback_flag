package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Full-width timeline for both live records and a detached saved archive. */
final class TimelineScreen extends FlagScreen {
    private final FlagManager manager;
    private final FlagBook book;
    private final UUID filter;
    private final String source;
    private String tag;
    private final TimelineViewport viewport = new TimelineViewport();
    private final Set<Long> expanded = new HashSet<>();
    private record Row(TickGroup group, FlagBook.Event event) { }
    private List<TickGroup> groups = List.of();
    private List<Row> rows = List.of();
    private final List<Button> buttons = new ArrayList<>();
    private Button previous, next, zoomIn, zoomOut, tagButton, save;
    private PositionSlider positionSlider;
    private int left, panelWidth, count, offset;
    private long revision = -1;
    private long origin;
    TimelineScreen(Screen parent, FlagManager manager, FlagBook book, UUID filter, String tag, String source) {
        super("timeline.title", parent); this.manager = manager; this.book = book; this.filter = filter; this.tag = tag; this.source = source;
    }
    @Override protected void init() {
        buttons.clear(); panelWidth = width - 24; left = 12;
        tagButton = button(tagLabel(), left, 30, panelWidth - 168, () -> minecraft.gui.setScreen(new TagFilterScreen(this, book.availableTags(), value -> {
            tag = value; offset = 0; viewport.reset(); expanded.clear();
        })));
        save = button(tr("archive.save"), left + panelWidth - 164, 30, 80, () -> minecraft.gui.setScreen(new ArchiveSaveScreen(this, book, selectedEvents(), source)));
        save.setTooltip(Tooltip.create(tr("archive.scope")));
        button(tr("archive.library"), left + panelWidth - 80, 30, 80, () -> minecraft.gui.setScreen(new ArchiveLibraryScreen(this)));
        zoomOut = button(Component.literal("−"), left, 56, 28, () -> changeZoom(2, .5)); zoomOut.setTooltip(Tooltip.create(tr("timeline.zoom_out")));
        zoomIn = button(Component.literal("+"), left + 32, 56, 28, () -> changeZoom(.5, .5)); zoomIn.setTooltip(Tooltip.create(tr("timeline.zoom_in")));
        button(Component.literal("<"), left + 64, 56, 28, () -> { viewport.pan(-.5); offset = 0; refresh(); }).setTooltip(Tooltip.create(tr("timeline.pan_left")));
        button(Component.literal(">"), left + 96, 56, 28, () -> { viewport.pan(.5); offset = 0; refresh(); }).setTooltip(Tooltip.create(tr("timeline.pan_right")));
        button(tr("timeline.fit"), left + 128, 56, 72, () -> { viewport.reset(); offset = 0; refresh(); });
        button(tr("timeline.latest"), left + 204, 56, 72, () -> { viewport.latest(); offset = 0; refresh(); });
        positionSlider = addRenderableWidget(new PositionSlider());
        count = Math.max(1, (height - 188) / 26);
        for (int i = 0; i < count; i++) buttons.add(addRenderableWidget(new RecordButton(i)));
        previous = button(Component.literal("<"), left, height - 29, 26, () -> { offset -= count; refresh(); });
        next = button(Component.literal(">"), left + 30, height - 29, 26, () -> { offset += count; refresh(); });
        button(tr("back"), left + panelWidth - 80, height - 29, 80, this::onClose);
        refresh();
    }
    private Component tagLabel() { return tr("tag_filter", tag == null ? tr("all_tags").getString() : tag.isEmpty() ? tr("untagged").getString() : tag); }
    private List<FlagBook.Event> selectedEvents() { return groups.stream().flatMap(g -> g.events().stream()).toList(); }
    private void changeZoom(double factor, double anchor) { viewport.zoom(factor, anchor); offset = 0; refresh(); }
    private Row row(int index) { int at = offset + index; return at >= 0 && at < rows.size() ? rows.get(at) : null; }
    private Component label(TickGroup group) {
        var text = tr(group.representative().worldTick() ? "ticks" : "legacy_ticks", book.relativeTick(group.representative())).copy();
        if (group.flags().size() > 1) text.append(" · ").append(tr("group_more", group.flags().size() - 1));
        return text.append(" · ").append(tr("group_changes", group.events().size()));
    }
    private void refresh() {
        var anchor = offset > 0 ? row(0) : null;
        var all = TickGroup.collect(book, filter, tag);
        viewport.update(TimelineScale.of(all.stream().map(TickGroup::representative).toList()));
        groups = all.stream().filter(g -> viewport.contains(g.representative())).toList();
        var ids = new HashSet<Long>(); all.forEach(g -> ids.add(g.id())); expanded.retainAll(ids);
        var newRows = new ArrayList<Row>();
        for (var g : groups) { newRows.add(new Row(g, null)); if (expanded.contains(g.id())) for (var e : g.events()) newRows.add(new Row(g, e)); }
        rows = List.copyOf(newRows);
        if (anchor != null) for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            if (anchor.event() == null ? r.event() == null && r.group().id() == anchor.group().id() : r.event() != null && r.event().sequence() == anchor.event().sequence()) { offset = i; break; }
        }
        offset = Math.clamp(offset, 0, Math.max(0, rows.size() - count));
        var data = book.data(); origin = data.tickOrigin() == null ? 0 : data.tickOrigin();
        for (int i = 0; i < buttons.size(); i++) {
            var row = row(i); var b = buttons.get(i); b.visible = row != null;
            if (row != null) {
                b.setMessage(row.event() == null ? Component.literal(expanded.contains(row.group().id()) ? "▼ " : "▶ ").append(label(row.group())) : Component.literal("↳ " + row.event().text()));
                b.setTooltip(Tooltip.create(row.event() == null ? tr("group_hint") : tr("event_hint", tr("kind." + row.event().kind().name().toLowerCase(Locale.ROOT)), row.event().target().coordinates())));
            }
        }
        previous.active = offset > 0; next.active = offset + count < rows.size();
        zoomIn.active = viewport.canZoomIn(); zoomOut.active = viewport.canZoomOut();
        positionSlider.sync();
        save.active = !groups.isEmpty(); tagButton.setMessage(tagLabel()); revision = book.revision();
    }
    private void activate(int index) {
        var row = row(index); if (row == null) return;
        if (row.event() != null) minecraft.gui.setScreen(manager == null ? new EventScreen(this, book, row.event()) : new EventScreen(this, manager, row.event()));
        else { if (!expanded.add(row.group().id())) expanded.remove(row.group().id()); refresh(); }
    }
    @Override public void tick() {
        if (manager != null && manager.book() != book) { minecraft.gui.setScreen(null); return; }
        if (book.revision() != revision) refresh();
    }
    private int x(TickGroup g) { return left + 7 + (int)(viewport.position(g.representative()) * (panelWidth - 14)); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 0 && event.y() >= 96 && event.y() <= 119) {
            var group = groups.stream().min(Comparator.comparingDouble(g -> Math.abs(event.x() - x(g)))).orElse(null);
            if (group != null && Math.abs(event.x() - x(group)) <= 6) {
                expanded.add(group.id()); refresh();
                for (int i = 0; i < rows.size(); i++) if (rows.get(i).group().id() == group.id()) { offset = i; break; }
                refresh(); return true;
            }
        }
        return super.mouseClicked(event, twice);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        if (y >= 96 && y <= 119) changeZoom(vertical > 0 ? .5 : 2, (x - left) / panelWidth);
        else { offset += vertical < 0 ? 1 : -1; refresh(); }
        return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        String range = viewport.worldTicks() ? ((long)Math.floor(viewport.start()) - origin) + " → " + ((long)Math.ceil(viewport.end()) - origin) + tr("timeline.tick_unit").getString()
                : tr("timeline.sequence", (long)Math.floor(viewport.start()), (long)Math.ceil(viewport.end())).getString();
        text(g, Component.literal(range).append(" · ").append(tr(manager == null ? "archive.readonly" : "timeline.live")), left, 84, panelWidth, MUTED);
        g.horizontalLine(left + 5, left + panelWidth - 5, 109, 0xFF52718C);
        for (var group : groups.reversed()) {
            int px = x(group); var flags = group.flags();
            for (int i = 0; i < Math.min(4, flags.size()); i++) g.fill(px - 3, 99 + i * 5, px + 4, 104 + i * 5, 0xFF000000 | flags.get(i).color());
            if (Math.abs(mx - px) <= 5 && my >= 96 && my <= 119) g.setTooltipForNextFrame(label(group), mx, my);
        }
        text(g, tr("timeline.count", groups.size(), groups.stream().mapToInt(v -> v.events().size()).sum()), left, 145, panelWidth, MUTED);
        if (groups.isEmpty()) text(g, tr("timeline.empty"), left, 167, panelWidth, MUTED);
        text(g, Component.literal(rows.isEmpty() ? "0/0" : (offset + 1) + "–" + Math.min(rows.size(), offset + count) + "/" + rows.size()), left + 64, height - 23, panelWidth - 150, MUTED);
    }
    private final class PositionSlider extends AbstractSliderButton {
        PositionSlider() { super(left, 121, panelWidth, 20, Component.empty(), 0); }
        void sync() { value = viewport.scrollPosition(); active = viewport.canZoomOut(); updateMessage(); }
        @Override protected void updateMessage() { setMessage(tr("timeline.position", (int)Math.round(value * 100))); }
        @Override protected void applyValue() { viewport.scrollTo(value); offset = 0; refresh(); }
    }
    private final class RecordButton extends Button {
        private final int index;
        RecordButton(int index) { super(left, 159 + index * 26, panelWidth, 24, Component.empty(), b -> activate(index), DEFAULT_NARRATION); this.index = index; }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            extractDefaultSprite(g); var row = row(index); if (row == null) return;
            text(g, getMessage(), getX() + 8, getY() + (row.event() == null ? 4 : 8), getWidth() - 16, row.event() == null ? WHITE : 0xFF000000 | row.event().color());
            if (row.event() == null) {
                var colors = row.group().flags(); double cell = Math.min(12d, (getWidth() - 16d) / colors.size());
                for (int i = 0; i < colors.size(); i++) { int x = getX() + 8 + (int)(i * cell); g.fill(x, getY() + 18, Math.max(x + 1, getX() + 8 + (int)((i + 1) * cell) - 2), getY() + 21, 0xFF000000 | colors.get(i).color()); }
            }
        }
    }
}
