package io.github.ruok0214.feedbackflag;

import java.util.*;

/** Game-independent history. Baselines are deliberately never persisted across observation gaps. */
public final class FlagBook {
    public static final int MAX_FLAGS = 128, MAX_EVENTS = 5000, MAX_TEXT = 32;
    public static final int MAX_MEMO = 1024, MAX_TAGS = 8, MAX_TAG_LENGTH = 24;
    public enum DisplayMode {
        FLAGS, BLOCKS, BOTH;
        public boolean flags() { return this != BLOCKS; }
        public boolean blocks() { return this != FLAGS; }
        public DisplayMode next() { return values()[(ordinal() + 1) % values().length]; }
    }
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
                       boolean watchBlocks, boolean watchProperties, long total, long lastTime, List<String> tags) {
        public Flag {
            Objects.requireNonNull(id); Objects.requireNonNull(target);
            text = cleanText(text); memo = cleanMemo(memo); tags = cleanTags(tags); color &= 0xFFFFFF;
            total = Math.max(0, total); lastTime = Math.max(0, lastTime);
        }
    }
    public enum Kind { BLOCK, STATE }
    public record Event(long sequence, long time, long tick, UUID flagId, Target target,
                        String text, int color, Kind kind, Snapshot before, Snapshot after, boolean worldTick, List<String> tags) {
        public Event { tags = cleanTags(tags); }
    }
    public record Data(int version, boolean enabled, int transparency, List<Flag> flags, List<Event> events,
                       long sequence, DisplayMode displayMode, Long tickOrigin, Long legacyTickOrigin, VisualSettings visuals,
                       List<Flag> deleted) {
        public Data(int version, boolean enabled, int transparency, List<Flag> flags, List<Event> events, long sequence,
                    DisplayMode displayMode, Long tickOrigin, Long legacyTickOrigin, VisualSettings visuals) {
            this(version, enabled, transparency, flags, events, sequence, displayMode, tickOrigin, legacyTickOrigin, visuals, List.of());
        }
        public Data(int version, boolean enabled, int transparency, List<Flag> flags, List<Event> events, long sequence) {
            this(version, enabled, transparency, flags, events, sequence, DisplayMode.FLAGS, null, null, VisualSettings.DEFAULT);
        }
    }

    private final LinkedHashMap<UUID, Flag> flags = new LinkedHashMap<>();
    private final Map<Target, UUID> targets = new HashMap<>();
    private final Map<Target, Snapshot> baselines = new HashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private List<Flag> deleted = List.of();
    public enum UndoStatus { READY, EMPTY, CONFLICT, LIMIT }
    private boolean enabled = true;
    private int transparency = 15;
    private long sequence, revision;
    private DisplayMode displayMode = DisplayMode.FLAGS;
    private VisualSettings visuals = VisualSettings.DEFAULT;
    private Long tickOrigin, legacyTickOrigin;

    public List<Flag> flags() { return List.copyOf(flags.values()); }
    public List<Event> events() { return List.copyOf(events); }
    public Flag flag(UUID id) { return flags.get(id); }
    public Flag at(Target target) { return flags.get(targets.get(target)); }
    public Snapshot baseline(Target target) { return baselines.get(target); }
    public boolean enabled() { return enabled; }
    public DisplayMode displayMode() { return displayMode; }
    public VisualSettings visuals() { return visuals; }
    public void setVisuals(VisualSettings value) {
        Objects.requireNonNull(value);
        if (!visuals.equals(value)) { visuals = value; revision++; }
    }
    public void setDisplayMode(DisplayMode mode) {
        Objects.requireNonNull(mode);
        if (displayMode != mode) { displayMode = mode; revision++; }
    }
    public long relativeTick(Event event) {
        Long origin = event.worldTick() ? tickOrigin : legacyTickOrigin;
        return origin == null ? 0 : event.tick() - origin;
    }
    public List<String> tags(Event event) {
        var flag = flag(event.flagId());
        return flag == null ? event.tags() : flag.tags();
    }
    public List<String> availableTags() {
        Map<String, String> result = new TreeMap<>();
        for (var flag : flags.values()) for (String tag : flag.tags()) result.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        for (var event : events) for (String tag : tags(event)) result.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        return List.copyOf(result.values());
    }
    public boolean matches(Event event, UUID flagId, String tag) {
        if (flagId != null && !flagId.equals(event.flagId())) return false;
        return tag == null || (tag.isEmpty() ? tags(event).isEmpty() : tags(event).stream().anyMatch(t -> t.equalsIgnoreCase(tag)));
    }
    public int transparency() { return transparency; }
    public long revision() { return revision; }
    public int deletedCount() { return deleted.size(); }
    public UndoStatus undoStatus() {
        if (deleted.isEmpty()) return UndoStatus.EMPTY;
        if (deleted.stream().anyMatch(f -> flags.containsKey(f.id()) || targets.containsKey(f.target()))) return UndoStatus.CONFLICT;
        return flags.size() + deleted.size() > MAX_FLAGS ? UndoStatus.LIMIT : UndoStatus.READY;
    }
    /** Restores the last deletion as one operation. Never overwrites a new flag or replays missed changes. */
    public boolean undoDeletion() {
        if (undoStatus() != UndoStatus.READY) return false;
        for (var flag : deleted) {
            flags.put(flag.id(), flag); targets.put(flag.target(), flag.id()); baselines.remove(flag.target());
        }
        deleted = List.of(); revision++;
        return true;
    }
    public void setEnabled(boolean value) { if (enabled != value) { enabled = value; revision++; } }
    public void setTransparency(int value) {
        int next = Math.clamp(value, 0, 100);
        if (transparency != next) { transparency = next; revision++; }
    }

    public Flag add(Target target, Snapshot initial, String text) {
        Flag existing = at(target);
        if (existing != null) return existing;
        if (flags.size() >= MAX_FLAGS) throw new IllegalStateException("Flag limit reached");
        Flag flag = new Flag(UUID.randomUUID(), target, text, "", 0x56C8E8, true, true, true, 0, 0, List.of());
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
        edit(id, text, memo, old.tags(), color, active, blocks, properties);
    }
    public void edit(UUID id, String text, String memo, List<String> tags, int color, boolean active, boolean blocks, boolean properties) {
        Flag old = flags.get(id);
        if (old == null) return;
        flags.put(id, new Flag(id, old.target(), text, memo, color, active, blocks, properties, old.total(), old.lastTime(), tags));
        revision++;
    }
    /** Validate the entire selection before changing anything. */
    public int editAll(Collection<UUID> selection, BulkEdit edit) {
        var changes = new LinkedHashMap<UUID, Flag>();
        for (UUID id : new LinkedHashSet<>(selection)) {
            var old = flags.get(id);
            if (old == null) throw new IllegalArgumentException("Selected flag no longer exists");
            var next = edit.apply(old);
            if (!old.equals(next)) changes.put(id, next);
        }
        if (!changes.isEmpty()) { flags.putAll(changes); revision++; }
        return changes.size();
    }
    public void remove(UUID id) {
        Flag old = flags.remove(id);
        if (old == null) return;
        deleted = List.of(old);
        targets.remove(old.target()); baselines.remove(old.target()); revision++;
        // Retain the last assigned tags when the live flag is removed.
        var retained = new ArrayDeque<Event>();
        for (var event : events) retained.add(event.flagId().equals(id)
                ? new Event(event.sequence(), event.time(), event.tick(), event.flagId(), event.target(), event.text(),
                    event.color(), event.kind(), event.before(), event.after(), event.worldTick(), old.tags()) : event);
        events.clear(); events.addAll(retained);
    }
    /** Removes every live flag while retaining the timeline and the final tags of removed flags. */
    public void removeAllFlags() {
        if (flags.isEmpty()) return;
        deleted = flags();
        var retained = new ArrayDeque<Event>();
        for (var event : events) {
            var flag = flags.get(event.flagId());
            retained.add(flag == null ? event : new Event(event.sequence(), event.time(), event.tick(), event.flagId(),
                    event.target(), event.text(), event.color(), event.kind(), event.before(), event.after(), event.worldTick(), flag.tags()));
        }
        events.clear(); events.addAll(retained);
        flags.clear(); targets.clear(); baselines.clear(); revision++;
    }
    /** Clears all history, or just one flag's, without interrupting observation or changing settings. */
    public void clearHistory(UUID id) {
        boolean changed = events.removeIf(event -> id == null || id.equals(event.flagId()));
        var pending = new ArrayList<Flag>();
        for (var flag : deleted) {
            if ((id == null || id.equals(flag.id())) && (flag.total() != 0 || flag.lastTime() != 0)) {
                pending.add(new Flag(flag.id(), flag.target(), flag.text(), flag.memo(), flag.color(), flag.enabled(),
                        flag.watchBlocks(), flag.watchProperties(), 0, 0, flag.tags()));
                changed = true;
            } else pending.add(flag);
        }
        deleted = List.copyOf(pending);
        for (var flag : flags()) {
            if ((id == null || id.equals(flag.id())) && (flag.total() != 0 || flag.lastTime() != 0)) {
                flags.put(flag.id(), new Flag(flag.id(), flag.target(), flag.text(), flag.memo(), flag.color(),
                        flag.enabled(), flag.watchBlocks(), flag.watchProperties(), 0, 0, flag.tags()));
                changed = true;
            }
        }
        if (id == null && (tickOrigin != null || legacyTickOrigin != null)) {
            tickOrigin = legacyTickOrigin = null; changed = true;
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
        if (tickOrigin == null) tickOrigin = tick;
        Event event = new Event(++sequence, time, tick, flag.id(), target, flag.text(), flag.color(), kind, previous, next, true, flag.tags());
        events.addLast(event);
        while (events.size() > MAX_EVENTS) events.removeFirst();
        flags.put(flag.id(), new Flag(flag.id(), target, flag.text(), flag.memo(), flag.color(), flag.enabled(),
                flag.watchBlocks(), flag.watchProperties(), flag.total() + 1, time, flag.tags()));
        revision++;
        return event;
    }
    public Data data() { return new Data(5, enabled, transparency, flags(), events(), sequence, displayMode, tickOrigin, legacyTickOrigin, visuals, deleted); }
    public static FlagBook restore(Data data) {
        if (data == null || (data.version() < 1 || data.version() > 5) || data.flags() == null || data.events() == null)
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
        if (data.deleted() != null) {
            if (data.deleted().size() > MAX_FLAGS) throw new IllegalArgumentException("Undo limit exceeded");
            var ids = new HashSet<UUID>(); var positions = new HashSet<Target>();
            for (var flag : data.deleted()) {
                if (flag == null || !ids.add(flag.id()) || !positions.add(flag.target()) || book.flags.containsKey(flag.id()))
                    throw new IllegalArgumentException("Invalid or duplicate deleted flag");
            }
            book.deleted = List.copyOf(data.deleted());
        }
        long last = 0;
        for (Event event : data.events()) {
            if (event == null || event.sequence() <= last || event.flagId() == null || event.target() == null
                    || event.before() == null || event.after() == null || event.kind() == null || event.text() == null)
                throw new IllegalArgumentException("Invalid event");
            last = event.sequence(); book.events.addLast(event);
        }
        book.sequence = Math.max(last, data.sequence());
        book.displayMode = data.displayMode() == null ? DisplayMode.FLAGS : data.displayMode();
        book.visuals = data.visuals() == null ? VisualSettings.DEFAULT : data.visuals();
        book.tickOrigin = data.tickOrigin(); book.legacyTickOrigin = data.legacyTickOrigin();
        for (var event : book.events) {
            if (event.worldTick() && book.tickOrigin == null) book.tickOrigin = event.tick();
            if (!event.worldTick() && book.legacyTickOrigin == null) book.legacyTickOrigin = event.tick();
        }
        return book;
    }
    public static List<String> parseTags(String value) {
        return cleanTags(value == null ? List.of() : Arrays.asList(value.split(",", -1)));
    }
    public static boolean validTags(String value) {
        if (value == null) return true;
        var unique = new HashSet<String>();
        for (String part : value.split(",", -1)) {
            String tag = safeTag(part);
            if (tag.codePointCount(0, tag.length()) > MAX_TAG_LENGTH) return false;
            if (!tag.isEmpty()) unique.add(tag.toLowerCase(Locale.ROOT));
        }
        return unique.size() <= MAX_TAGS;
    }
    private static String safeTag(String value) {
        if (value == null) return "";
        var result = new StringBuilder();
        value.codePoints().filter(c -> !Character.isISOControl(c) && c != 0x00A7 && c != ',').forEach(result::appendCodePoint);
        return result.toString().strip();
    }
    public static List<String> cleanTags(List<String> input) {
        if (input == null) return List.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : input) {
            String tag = safeTag(item);
            int count = tag.codePointCount(0, tag.length());
            if (count > MAX_TAG_LENGTH) tag = tag.substring(0, tag.offsetByCodePoints(0, MAX_TAG_LENGTH));
            if (!tag.isEmpty()) result.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            if (result.size() == MAX_TAGS) break;
        }
        return List.copyOf(result.values());
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
