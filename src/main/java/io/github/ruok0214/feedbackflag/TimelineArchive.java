package io.github.ruok0214.feedbackflag;

import java.util.*;

/** A read-only capture with its original tick origins and the tags/memos at save time. */
public record TimelineArchive(String format, int version, String name, String source, long savedAt, FlagBook.Data data) {
    public TimelineArchive {
        if (!"feedback-flag-timeline".equals(format) || version != 1 || name == null || source == null || savedAt < 0)
            throw new IllegalArgumentException("Invalid timeline archive");
        name = name.codePoints().filter(c -> !Character.isISOControl(c)).limit(64).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
        if (name.isBlank()) throw new IllegalArgumentException("Missing archive name");
        if (source.length() > 1024) throw new IllegalArgumentException("Invalid source");
        FlagBook.restore(data);
    }
    public FlagBook book() { return FlagBook.restore(data); }
    public static TimelineArchive capture(FlagBook book, List<FlagBook.Event> selected, String name, String source) {
        Set<Long> ids = new HashSet<>(); selected.forEach(e -> ids.add(e.sequence()));
        var events = book.events().stream().filter(e -> ids.contains(e.sequence())).map(e -> new FlagBook.Event(e.sequence(), e.time(), e.tick(), e.flagId(), e.target(),
                e.text(), e.color(), e.kind(), e.before(), e.after(), e.worldTick(), book.tags(e))).toList();
        Set<UUID> flags = new HashSet<>(); events.forEach(e -> flags.add(e.flagId()));
        var data = book.data();
        return new TimelineArchive("feedback-flag-timeline", 1, name, source, System.currentTimeMillis(),
                new FlagBook.Data(5, data.enabled(), data.transparency(), book.flags().stream().filter(f -> flags.contains(f.id())).toList(),
                        events, data.sequence(), data.displayMode(), data.tickOrigin(), data.legacyTickOrigin(), data.visuals(), List.of()));
    }
}
