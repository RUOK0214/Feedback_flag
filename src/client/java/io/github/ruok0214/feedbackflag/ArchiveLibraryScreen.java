package io.github.ruok0214.feedbackflag;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

final class ArchiveLibraryScreen extends FlagScreen {
    private List<TimelineFiles.Entry> entries = List.of();
    private final List<Button> rows = new ArrayList<>();
    private CompletableFuture<List<TimelineFiles.Entry>> listing;
    private CompletableFuture<TimelineArchive> loading;
    private Button previous, next;
    private int left, panelWidth, count, page;
    private String status;
    static TimelineFiles files() { return new TimelineFiles(FabricLoader.getInstance().getConfigDir().resolve("feedback_flag/archives")); }
    ArchiveLibraryScreen(Screen parent) { super("archive.library", parent); }
    @Override protected void init() {
        rows.clear(); panelWidth = Math.min(650, width - 24); left = (width - panelWidth) / 2;
        button(tr("archive.refresh"), left, 32, (panelWidth - 6) / 2, this::reload);
        button(tr("archive.folder"), left + (panelWidth + 6) / 2, 32, (panelWidth - 6) / 2, () -> {
            try { java.nio.file.Files.createDirectories(files().root()); net.minecraft.util.Util.getPlatform().openPath(files().root()); }
            catch (IOException ex) { status = "archive.load_failed"; }
        });
        count = Math.max(1, (height - 106) / 26);
        for (int i = 0; i < count; i++) { final int row = i; rows.add(button(Component.empty(), left, 75 + i * 26, panelWidth, () -> open(page * count + row))); }
        previous = button(Component.literal("<"), left, height - 29, 26, () -> { page--; refresh(); });
        next = button(Component.literal(">"), left + 30, height - 29, 26, () -> { page++; refresh(); });
        button(tr("back"), left + panelWidth - 80, height - 29, 80, this::onClose);
        reload(); refresh();
    }
    private void reload() {
        status = "archive.loading";
        listing = CompletableFuture.supplyAsync(() -> { try { return files().list(); } catch (IOException ex) { throw new CompletionException(ex); } });
    }
    private void open(int index) {
        if (index >= entries.size() || loading != null) return;
        var entry = entries.get(index); status = "archive.loading";
        loading = CompletableFuture.supplyAsync(() -> { try { return files().read(entry.folder()); } catch (IOException ex) { throw new CompletionException(ex); } }); refresh();
    }
    private void refresh() {
        page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / count));
        for (int i = 0; i < rows.size(); i++) {
            int at = page * count + i; var b = rows.get(i); b.visible = at < entries.size();
            if (!b.visible) continue; var e = entries.get(at); b.active = loading == null && e.valid();
            b.setMessage(Component.literal(fit(e.name() + " · " + e.events(), panelWidth - 14)));
            b.setTooltip(Tooltip.create(e.valid() ? Component.literal(e.source() + "\n" + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.savedAt()))) : tr("archive.invalid")));
        }
        previous.active = page > 0; next.active = (page + 1) * count < entries.size();
    }
    @Override public void tick() {
        if (listing != null && listing.isDone()) {
            try { entries = listing.join(); status = entries.isEmpty() ? "archive.empty" : "archive.library_hint"; }
            catch (CompletionException ex) { status = "archive.load_failed"; }
            listing = null; refresh();
        }
        if (loading != null && loading.isDone()) {
            try { var a = loading.join(); loading = null; minecraft.gui.setScreen(new TimelineScreen(this, null, a.book(), null, null, a.source())); }
            catch (CompletionException ex) { loading = null; status = "archive.load_failed"; refresh(); }
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false; page += vertical < 0 ? 1 : -1; refresh(); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        if (status != null) text(g, tr(status), left, 60, panelWidth, MUTED);
        text(g, Component.literal((page + 1) + "/" + Math.max(1, (entries.size() + count - 1) / count)), left + 64, height - 23, panelWidth - 150, MUTED);
    }
}
