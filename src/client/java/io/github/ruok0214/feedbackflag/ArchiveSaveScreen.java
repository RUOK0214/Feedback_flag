package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class ArchiveSaveScreen extends FlagScreen {
    private final TimelineArchive captured;
    private String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));
    private CompletableFuture<Path> future;
    private Path saved;
    private String status = "archive.save_hint";
    private int left, panelWidth;
    ArchiveSaveScreen(Screen parent, FlagBook book, List<FlagBook.Event> events, String source) {
        super("archive.save_title", parent); captured = TimelineArchive.capture(book, events, name, source);
    }
    private TimelineArchive archive() { return new TimelineArchive(captured.format(), captured.version(), name.strip(), captured.source(), captured.savedAt(), captured.data()); }
    @Override protected void init() {
        panelWidth = Math.min(560, width - 24); left = (width - panelWidth) / 2;
        var nameBox = addRenderableWidget(new EditBox(font, left, 56, panelWidth, 20, tr("archive.name")));
        nameBox.setMaxLength(64); nameBox.setValue(name); nameBox.setEditable(future == null && saved == null);
        var save = button(tr("archive.save"), left, 110, panelWidth, () -> {
            if (name.isBlank()) return;
            var snapshot = archive(); status = "archive.saving";
            future = CompletableFuture.supplyAsync(() -> { try { return ArchiveLibraryScreen.files().save(snapshot); } catch (java.io.IOException ex) { throw new CompletionException(ex); } }); rebuildWidgets();
        });
        save.active = future == null && saved == null && !name.isBlank();
        nameBox.setResponder(value -> { name = value; save.active = future == null && saved == null && !name.isBlank(); });
        if (saved != null) {
            int third = (panelWidth - 8) / 3;
            button(tr("archive.view"), left, 140, third, () -> minecraft.gui.setScreen(new TimelineScreen(this, null, archive().book(), null, null, captured.source())));
            button(tr("archive.browser"), left + third + 4, 140, third, () -> net.minecraft.util.Util.getPlatform().openPath(saved.resolve("timeline.html")));
            button(tr("archive.folder"), left + (third + 4) * 2, 140, third, () -> net.minecraft.util.Util.getPlatform().openPath(saved));
        }
        button(tr("back"), left, height - 28, panelWidth, this::onClose);
    }
    @Override public void tick() {
        if (future != null && future.isDone()) {
            try { saved = future.join(); status = "archive.saved"; }
            catch (CompletionException ex) { status = "archive.save_failed"; }
            future = null; rebuildWidgets();
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta); text(g, tr("archive.name"), left, 41, panelWidth, MUTED);
        text(g, tr("archive.capture_count", captured.data().events().size()), left, 86, panelWidth, MUTED);
        g.textWithWordWrap(font, tr(status), left, 174, panelWidth, status.endsWith("failed") ? 0xFFFF8888 : MUTED);
    }
}
