package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DashboardScreen extends FlagScreen {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final FlagManager manager;
    private final UUID filter;
    private int flagPage, eventPage, leftWidth, rightX, rightWidth, flagRows, eventRows;
    private List<FlagBook.Flag> flags = List.of();
    private List<FlagBook.Event> timeline = List.of();
    private final List<Button> flagButtons = new ArrayList<>(), eventButtons = new ArrayList<>();
    private Button flagPrevious, flagNext, eventPrevious, eventNext;
    private long revision = -1;

    public DashboardScreen(FlagManager manager, UUID filter) {
        super("title", null); this.manager = manager; this.filter = filter;
    }
    @Override protected void init() {
        flagButtons.clear(); eventButtons.clear();
        leftWidth = Math.max(104, (width - 30) / 3);
        rightX = leftWidth + 20; rightWidth = width - rightX - 10;
        int actionWidth = (width - 28) / 3;
        // Button labels can change without rebuilding sliders or losing keyboard focus.
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
        flagRows = Math.max(1, (height - 142) / 27);
        eventRows = Math.max(1, (height - 181) / 24);
        for (int i = 0; i < flagRows; i++) {
            final int row = i;
            flagButtons.add(button(Component.empty(), 10, 103 + i * 27, leftWidth, () -> {
                int index = flagPage * flagRows + row;
                if (index < flags.size()) minecraft.gui.setScreen(new FlagEditScreen(this, manager, flags.get(index).id()));
            }));
        }
        for (int i = 0; i < eventRows; i++) {
            final int row = i;
            eventButtons.add(button(Component.empty(), rightX, 143 + i * 24, rightWidth, () -> {
                var event = eventAt(row);
                if (event != null) minecraft.gui.setScreen(new EventScreen(this, manager, event));
            }));
        }
        flagPrevious = button(Component.literal("<"), 10, height - 29, 22, () -> { flagPage--; refresh(false); });
        flagNext = button(Component.literal(">"), 36, height - 29, 22, () -> { flagPage++; refresh(false); });
        eventPrevious = button(Component.literal("<"), rightX, height - 29, 22, () -> { eventPage--; refresh(false); });
        eventNext = button(Component.literal(">"), rightX + 26, height - 29, 22, () -> { eventPage++; refresh(false); });
        button(tr(filter == null ? "close" : "all_flags"), width - 88, height - 29, 78, () -> {
            if (filter == null) onClose(); else minecraft.gui.setScreen(new DashboardScreen(manager, null));
        });
        revision = -1; refresh(true);
    }
    @Override public void tick() {
        if (manager.book() == null) { onClose(); return; }
        if (revision != manager.book().revision()) refresh(eventPage == 0);
    }
    private void refresh(boolean updateTimeline) {
        flags = manager.book().flags();
        if (updateTimeline || timeline.isEmpty())
            timeline = manager.book().events().reversed().stream().filter(e -> filter == null || filter.equals(e.flagId())).toList();
        revision = manager.book().revision();
        flagPage = Math.clamp(flagPage, 0, Math.max(0, (flags.size() - 1) / flagRows));
        eventPage = Math.clamp(eventPage, 0, Math.max(0, (timeline.size() - 1) / eventRows));
        for (int i = 0; i < flagButtons.size(); i++) {
            int index = flagPage * flagRows + i;
            var b = flagButtons.get(i); b.visible = index < flags.size();
            if (!b.visible) continue;
            var f = flags.get(index);
            String label = (f.text().isBlank() ? "Flag" : f.text()) + " · " + f.total();
            b.setMessage(Component.literal(fit(label, leftWidth - 14)).withColor(f.color()));
            b.setTooltip(Tooltip.create(tr("flag_summary", f.target().coordinates(), tr(manager.status(f)), f.total())));
        }
        for (int i = 0; i < eventButtons.size(); i++) {
            var event = eventAt(i); var b = eventButtons.get(i); b.visible = event != null;
            if (event == null) continue;
            b.setMessage(Component.literal(fit(TIME.format(Instant.ofEpochMilli(event.time())) + "  " + event.text(), rightWidth - 12)).withColor(event.color()));
            b.setTooltip(Tooltip.create(tr("event_hint", tr("kind." + event.kind().name().toLowerCase(java.util.Locale.ROOT)), event.target().coordinates())));
        }
        flagPrevious.active = flagPage > 0; flagNext.active = (flagPage + 1) * flagRows < flags.size();
        eventPrevious.active = eventPage > 0; eventNext.active = (eventPage + 1) * eventRows < timeline.size();
    }
    private FlagBook.Event eventAt(int row) {
        int index = eventPage * eventRows + row;
        return index < timeline.size() ? timeline.get(index) : null;
    }
    private int dotX(FlagBook.Event event) {
        var first = eventAt(0);
        var last = eventAt(Math.min(eventRows, timeline.size() - eventPage * eventRows) - 1);
        if (first == null || last == null || first.time() == last.time()) return rightX + rightWidth / 2;
        double fraction = (double)(event.time() - last.time()) / (first.time() - last.time());
        return rightX + 7 + (int)(fraction * (rightWidth - 14));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 0 && event.y() >= 102 && event.y() <= 124) {
            for (int i = 0; i < eventRows; i++) {
                var item = eventAt(i);
                if (item != null && Math.abs(event.x() - dotX(item)) <= 5) {
                    minecraft.gui.setScreen(new EventScreen(this, manager, item)); return true;
                }
            }
        }
        return super.mouseClicked(event, twice);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x < rightX) flagPage += vertical < 0 ? 1 : -1;
        else eventPage += vertical < 0 ? 1 : -1;
        refresh(eventPage == 0); return true;
    }
    @Override public void removed() { manager.save(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.fill(rightX - 6, 80, rightX - 5, height - 35, 0xFF33465B);
        long active = flags.stream().filter(f -> manager.status(f).equals("watching")).count();
        String error = manager.errorKey();
        text(g, error == null ? tr("summary", active, flags.size(), manager.book().events().size()) : tr(error), rightX, 60, rightWidth, error == null ? MUTED : 0xFFFF8888);
        text(g, tr("flags"), 10, 86, leftWidth, WHITE);
        text(g, tr(filter == null ? "timeline" : "filtered_timeline"), rightX, 86, rightWidth, WHITE);
        if (flags.isEmpty()) {
            g.textWithWordWrap(font, tr("no_flags"), 12, 110, leftWidth - 4, MUTED);
        } else for (int i = 0; i < flagRows; i++) {
            int index = flagPage * flagRows + i;
            if (index >= flags.size()) break;
            String status = manager.status(flags.get(index));
            g.fill(11, 104 + i * 27, 14, 122 + i * 27, status.equals("watching") ? 0xFF66D9A0 : status.equals("paused") ? 0xFFDAAD60 : 0xFF697586);
        }
        g.horizontalLine(rightX + 5, rightX + rightWidth - 5, 112, 0xFF48647F);
        for (int i = eventRows - 1; i >= 0; i--) {
            var event = eventAt(i); if (event == null) continue;
            int px = dotX(event);
            g.fill(px - 3, 108, px + 4, 117, 0xFF000000 | event.color());
            if (Math.abs(x - px) <= 5 && y >= 102 && y <= 124)
                g.setTooltipForNextFrame(Component.literal(TIME.format(Instant.ofEpochMilli(event.time())) + " · " + event.text()), x, y);
        }
        if (timeline.isEmpty()) text(g, tr("no_events"), rightX + 2, 128, rightWidth - 4, MUTED);
        else {
            var oldest = eventAt(Math.min(eventRows, timeline.size() - eventPage * eventRows) - 1);
            text(g, Component.literal(TIME.format(Instant.ofEpochMilli(oldest.time())) + " → " + TIME.format(Instant.ofEpochMilli(eventAt(0).time()))), rightX, 128, rightWidth, MUTED);
        }
        text(g, Component.literal((flagPage + 1) + "/" + Math.max(1, (flags.size() + flagRows - 1) / flagRows)), 64, height - 23, leftWidth - 54, MUTED);
        text(g, Component.literal((eventPage + 1) + "/" + Math.max(1, (timeline.size() + eventRows - 1) / eventRows)), rightX + 54, height - 23, Math.max(1, rightWidth - 138), MUTED);
    }
}
