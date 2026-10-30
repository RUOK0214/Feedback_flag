package io.github.ruok0214.feedbackflag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class TimelineFiles {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Path root;
    public record Entry(Path folder, String name, String source, long savedAt, int events, boolean valid) { }
    public TimelineFiles(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Path root() { return root; }
    public List<Entry> list() throws IOException {
        if (!Files.exists(root)) return List.of();
        var result = new ArrayList<Entry>();
        try (var paths = Files.list(root)) {
            for (var folder : paths.filter(p -> !p.getFileName().toString().startsWith(".") && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (!Files.isRegularFile(folder.resolve("timeline.json"), LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    var archive = read(folder);
                    result.add(new Entry(folder, archive.name(), archive.source(), archive.savedAt(), archive.data().events().size(), true));
                } catch (IOException ex) { result.add(new Entry(folder, folder.getFileName().toString(), "", 0, 0, false)); }
            }
        }
        result.sort(Comparator.comparingLong(Entry::savedAt).reversed().thenComparing(Entry::name));
        return List.copyOf(result);
    }
    public TimelineArchive read(Path folder) throws IOException {
        folder = folder.toAbsolutePath().normalize();
        if (!root.equals(folder.getParent()) || Files.isSymbolicLink(folder)) throw new IOException("Invalid archive location");
        Path file = folder.resolve("timeline.json");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) throw new IOException("Invalid archive size or type");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var archive = JSON.fromJson(reader, TimelineArchive.class);
            if (archive == null) throw new IllegalArgumentException("Empty archive");
            return archive;
        } catch (RuntimeException ex) { throw new IOException("Invalid timeline file", ex); }
    }
    public Path save(TimelineArchive archive) throws IOException {
        String json = JSON.toJson(archive);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Archive exceeds 16 MiB");
        Files.createDirectories(root);
        String id = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(archive.savedAt())) + "-" + UUID.randomUUID();
        Path temporary = Files.createDirectory(root.resolve(".pending-" + id)), destination = root.resolve(id);
        try {
            Files.writeString(temporary.resolve("timeline.json"), json, StandardCharsets.UTF_8);
            Files.writeString(temporary.resolve("timeline.html"), html(archive), StandardCharsets.UTF_8);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
            return destination;
        } finally {
            Files.deleteIfExists(temporary.resolve("timeline.json")); Files.deleteIfExists(temporary.resolve("timeline.html")); Files.deleteIfExists(temporary);
        }
    }
    public static String html(TimelineArchive archive) throws IOException {
        var book = archive.book(); var scale = TimelineScale.of(book.events().reversed());
        var rows = new ArrayList<Map<String, Object>>();
        for (var e : book.events()) {
            var f = book.flag(e.flagId()); var row = new LinkedHashMap<String, Object>();
            row.put("id", e.sequence()); row.put("flag", e.flagId().toString()); row.put("tick", Long.toString(book.relativeTick(e)));
            row.put("axis", scale.worldTicks() ? book.relativeTick(e) : e.sequence()); row.put("clock", e.worldTick());
            row.put("rawTick", Long.toString(e.tick())); row.put("name", e.text()); row.put("color", String.format(Locale.ROOT, "#%06X", e.color() & 0xFFFFFF));
            row.put("tags", book.tags(e)); row.put("target", e.target().dimension() + " · " + e.target().coordinates());
            row.put("kind", e.kind().name()); row.put("before", e.before().describe()); row.put("after", e.after().describe()); row.put("memo", f == null ? "" : f.memo()); rows.add(row);
        }
        var payload = Map.of("name", archive.name(), "source", archive.source(), "savedAt", archive.savedAt(), "ticks", scale.worldTicks(), "events", rows);
        try (var input = TimelineFiles.class.getResourceAsStream("/assets/feedback_flag/timeline.html")) {
            if (input == null) throw new IOException("Missing HTML viewer");
            // Gson's default HTML escaping prevents a name/memo from closing the JSON script element.
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("__TIMELINE_DATA__", JSON.toJson(payload));
        }
    }
}
