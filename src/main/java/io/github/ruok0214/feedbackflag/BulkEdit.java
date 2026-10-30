package io.github.ruok0214.feedbackflag;

import java.util.*;

/** Null color and KEEP switches leave each flag's value alone. */
public record BulkEdit(Integer color, Switch enabled, Switch blocks, Switch states, Tags tagMode, List<String> tags) {
    public enum Switch {
        KEEP, ON, OFF;
        public Switch next() { return values()[(ordinal() + 1) % values().length]; }
        boolean apply(boolean old) { return this == KEEP ? old : this == ON; }
    }
    public enum Tags {
        KEEP, ADD, REMOVE, REPLACE;
        public Tags next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public BulkEdit {
        Objects.requireNonNull(enabled); Objects.requireNonNull(blocks); Objects.requireNonNull(states); Objects.requireNonNull(tagMode);
        tags = FlagBook.cleanTags(tags);
    }
    public FlagBook.Flag apply(FlagBook.Flag flag) {
        var merged = new LinkedHashMap<String, String>();
        if (tagMode != Tags.REPLACE) for (String tag : flag.tags()) merged.put(tag.toLowerCase(Locale.ROOT), tag);
        if (tagMode != Tags.KEEP) for (String tag : tags) {
            if (tagMode == Tags.REMOVE) merged.remove(tag.toLowerCase(Locale.ROOT));
            else merged.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        if (merged.size() > FlagBook.MAX_TAGS) throw new IllegalArgumentException("Too many resulting tags");
        return new FlagBook.Flag(flag.id(), flag.target(), flag.text(), flag.memo(), color == null ? flag.color() : color,
                enabled.apply(flag.enabled()), blocks.apply(flag.watchBlocks()), states.apply(flag.watchProperties()),
                flag.total(), flag.lastTime(), List.copyOf(merged.values()));
    }
}
