package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

final class EventScreen extends FlagScreen {
    private final FlagManager manager;
    private final FlagBook.Event event;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int scroll, left, panelWidth;
    EventScreen(Screen parent, FlagManager manager, FlagBook.Event event) {
        super("event_title", parent); this.manager = manager; this.event = event;
    }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 28); left = (width - panelWidth) / 2;
        lines.clear();
        line(Component.literal(event.text() + "  ·  #" + event.sequence()).withColor(event.color()));
        line(tr("event_time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(event.time()))));
        line(tr("event_tick", event.tick()));
        line(Component.literal(event.target().dimension() + " · " + event.target().coordinates()));
        line(tr("kind." + event.kind().name().toLowerCase(java.util.Locale.ROOT)));
        line(Component.empty());
        line(tr("before")); line(Component.literal(event.before().describe()));
        line(Component.empty());
        line(tr("after")); line(Component.literal(event.after().describe()));
        line(Component.empty()); line(tr("changed_properties"));
        if (!event.before().block().equals(event.after().block()))
            line(Component.literal(event.before().block() + " → " + event.after().block()));
        var names = new TreeSet<>(event.before().properties().keySet()); names.addAll(event.after().properties().keySet());
        for (String name : names) {
            String before = event.before().properties().getOrDefault(name, "—");
            String after = event.after().properties().getOrDefault(name, "—");
            if (!before.equals(after)) line(Component.literal(name + ": " + before + " → " + after));
        }
        line(Component.empty()); line(tr("observation_note"));
        button(tr("back"), left, height - 28, (panelWidth - 6) / 2, this::onClose);
        button(tr("edit_title"), left + (panelWidth + 6) / 2, height - 28, (panelWidth - 6) / 2,
                () -> minecraft.gui.setScreen(new FlagEditScreen(this, manager, event.flagId())))
                .active = manager.book().flag(event.flagId()) != null;
        clampScroll();
    }
    private void line(Component text) { lines.addAll(font.split(text, panelWidth - 12)); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - Math.max(1, (height - 76) / 12))); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll -= (int)Math.signum(vertical) * 3; clampScroll(); return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.isDown() || event.isUp()) { scroll += event.isDown() ? 1 : -1; clampScroll(); return true; }
        return super.keyPressed(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.enableScissor(left, 30, left + panelWidth, height - 42);
        for (int i = scroll; i < lines.size(); i++) {
            int py = 34 + (i - scroll) * 12; if (py > height - 43) break;
            g.text(font, lines.get(i), left + 3, py, WHITE);
        }
        g.disableScissor();
        if (lines.size() * 12 > height - 76) text(g, tr("scroll"), left, height - 40, panelWidth, MUTED);
    }
}
