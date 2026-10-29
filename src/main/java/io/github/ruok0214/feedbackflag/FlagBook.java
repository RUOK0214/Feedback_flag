package io.github.ruok0214.feedbackflag;

import java.util.*;

/** Game-independent history. Baselines are deliberately never persisted across observation gaps. */
public final class FlagBook {
    public static final int MAX_FLAGS = 128, MAX_EVENTS = 5000, MAX_TEXT = 32;
    public static final int MAX_MEMO = 1024;
    public record Target(String dimension, int x, int y, int z) {
        public Target { Objects.requireNonNull(dimension); }
        public String coordinates() { return x + ", " + y + ", " + z; }
    }
    public record Snapshot(String block, Map<String, String> properties) {
        public Snapshot { Objects.requireNonNull(block); properties = Map.copyOf(properties); }
        public String describe() {
            if (properties.isEmpty()) return block;
            return block + new TreeMap<>(properties);
        }
    }
    public record Flag(UUID id, Target target, String text, String memo, int color, boolean enabled,
                       boolean watchBlocks, boolean watchProperties, long total, long lastTime) {
        public Flag {
            Objects.requireNonNull(id); Objects.requireNonNull(target);
            text = cleanText(text); memo = cleanMemo(memo); color &= 0xFFFFFF;
            total = Math.max(0, total); lastTime = Math.max(0, lastTime);
        }
    }
    public enum Kind { BLOCK, STATE }
    public record Event(long sequence, long time, long tick, UUID flagId, Target target,
                        String text, int color, Kind kind, Snapshot before, Snapshot after, boolean worldTick) { }
    public record Data(int version, boolean enabled, int transparency, List<Flag> flags, List<Event> events, long sequence) { }

    private final LinkedHashMap<UUID, Flag> flags = new LinkedHashMap<>();
    private final Map<Target, UUID> targets = new HashMap<>();
    private final Map<Target, Snapshot> baselines = new HashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private boolean enabled = true;
    private int transparency = 15;
    private long sequence, revision;

    public List<Flag> flags() { return List.copyOf(flags.values()); }
    public List<Event> events() { return List.copyOf(events); }
    public Flag flag(UUID id) { return flags.get(id); }
    public Flag at(Target target) { return flags.get(targets.get(target)); }
    public Snapshot baseline(Target target) { return baselines.get(target); }
    public boolean enabled() { return enabled; }
    public int transparency() { return transparency; }
    public long revision() { return revision; }
    public void setEnabled(boolean value) { if (enabled != value) { enabled = value; revision++; } }
    public void setTransparency(int value) {
        int next = Math.clamp(value, 0, 100);
        if (transparency != next) { transparency = next; revision++; }
    }

    public Flag add(Target target, Snapshot initial, String text) {
        Flag existing = at(target);
        if (existing != null) return existing;
        if (flags.size() >= MAX_FLAGS) throw new IllegalStateException("Flag limit reached");
        Flag flag = new Flag(UUID.randomUUID(), target, text, "", 0x56C8E8, true, true, true, 0, 0);
        flags.put(flag.id(), flag); targets.put(target, flag.id());
        seed(target, initial); revision++;
        return flag;
    }
    public void edit(UUID id, String text, int color, boolean active, boolean blocks, boolean properties) {
        Flag old = flags.get(id);
        if (old == null) return;
        edit(id, text, old.memo(), color, active, blocks, properties);
    }
    public void edit(UUID id, String text, String memo, int color, boolean active, boolean blocks, boolean properties) {
        Flag old = flags.get(id);
        if (old == null) return;
        flags.put(id, new Flag(id, old.target(), text, memo, color, active, blocks, properties, old.total(), old.lastTime()));
        revision++;
    }
    public void remove(UUID id) {
        Flag old = flags.remove(id);
        if (old == null) return;
        targets.remove(old.target()); baselines.remove(old.target()); revision++;
        // Historical events retain a copy of the flag's identity, label and color.
    }
    /** Clears all history, or just one flag's, without interrupting observation or changing settings. */
    public void clearHistory(UUID id) {
        boolean changed = events.removeIf(event -> id == null || id.equals(event.flagId()));
        for (var flag : flags()) {
            if ((id == null || id.equals(flag.id())) && (flag.total() != 0 || flag.lastTime() != 0)) {
                flags.put(flag.id(), new Flag(flag.id(), flag.target(), flag.text(), flag.memo(), flag.color(),
                        flag.enabled(), flag.watchBlocks(), flag.watchProperties(), 0, 0));
                changed = true;
            }
        }
        // Keep sequence numbers unique even when another screen still holds a previous event.
        if (changed) revision++;
    }
    public void seed(Target target, Snapshot snapshot) {
        if (targets.containsKey(target) && snapshot != null) baselines.put(target, snapshot);
    }
    public void forget(Target target) { baselines.remove(target); }
    public void forgetAll() { baselines.clear(); }

    public Event observe(Target target, Snapshot next, long time, long tick) {
        Flag flag = at(target);
        if (flag == null || next == null) return null;
        Snapshot previous = baselines.put(target, next);
        // Always advance the baseline while paused, so re-enabling cannot report old changes.
        if (previous == null || previous.equals(next) || !enabled || !flag.enabled()) return null;
        Kind kind = previous.block().equals(next.block()) ? Kind.STATE : Kind.BLOCK;
        if (kind == Kind.BLOCK ? !flag.watchBlocks() : !flag.watchProperties()) return null;
        Event event = new Event(++sequence, time, tick, flag.id(), target, flag.text(), flag.color(), kind, previous, next, true);
        events.addLast(event);
        while (events.size() > MAX_EVENTS) events.removeFirst();
        flags.put(flag.id(), new Flag(flag.id(), target, flag.text(), flag.memo(), flag.color(), flag.enabled(),
                flag.watchBlocks(), flag.watchProperties(), flag.total() + 1, time));
        revision++;
        return event;
    }
    public Data data() { return new Data(2, enabled, transparency, flags(), events(), sequence); }
    public static FlagBook restore(Data data) {
        if (data == null || (data.version() != 1 && data.version() != 2) || data.flags() == null || data.events() == null)
            throw new IllegalArgumentException("Unsupported or incomplete history");
        if (data.flags().size() > MAX_FLAGS || data.events().size() > MAX_EVENTS)
            throw new IllegalArgumentException("History limits exceeded");
        FlagBook book = new FlagBook();
        book.enabled = data.enabled(); book.transparency = Math.clamp(data.transparency(), 0, 100);
        for (Flag flag : data.flags()) {
            if (flag == null || book.flags.putIfAbsent(flag.id(), flag) != null
                    || book.targets.putIfAbsent(flag.target(), flag.id()) != null)
                throw new IllegalArgumentException("Invalid or duplicate flag");
        }
        long last = 0;
        for (Event event : data.events()) {
            if (event == null || event.sequence() <= last || event.flagId() == null || event.target() == null
                    || event.before() == null || event.after() == null || event.kind() == null || event.text() == null)
                throw new IllegalArgumentException("Invalid event");
            last = event.sequence(); book.events.addLast(event);
        }
        book.sequence = Math.max(last, data.sequence());
        return book;
    }
    public static String cleanText(String value) {
        if (value == null) return "";
        StringBuilder text = new StringBuilder();
        value.codePoints().filter(c -> !Character.isISOControl(c) && c != 0x00A7)
                .limit(MAX_TEXT).forEach(text::appendCodePoint);
        return text.toString().strip();
    }
    /** An absent memo in a 0.1.0 save becomes empty; newlines and Unicode are preserved. */
    public static String cleanMemo(String value) {
        String normalized = normalizeMemo(value);
        int length = normalized.codePointCount(0, normalized.length());
        return length <= MAX_MEMO ? normalized : normalized.substring(0, normalized.offsetByCodePoints(0, MAX_MEMO));
    }
    public static String normalizeMemo(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").codePoints()
                .filter(c -> (c == '\n' || !Character.isISOControl(c)) && c != 0x00A7)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
