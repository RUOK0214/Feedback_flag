package io.github.ruok0214.feedbackflag;

import java.util.*;

/** One contiguous tick, newest events first. Group before filtering so expansion identity stays stable. */
public record TickGroup(long id, List<FlagBook.Event> events) {
    public TickGroup { events = List.copyOf(events); }
    public FlagBook.Event representative() { return events.getFirst(); }
    public List<FlagBook.Event> flags() {
        Map<UUID, FlagBook.Event> unique = new LinkedHashMap<>();
        for (var event : events) unique.putIfAbsent(event.flagId(), event);
        return List.copyOf(unique.values());
    }
    public static List<TickGroup> collect(FlagBook book, UUID flag, String tag) {
        List<TickGroup> result = new ArrayList<>();
        List<FlagBook.Event> selected = new ArrayList<>();
        FlagBook.Event previous = null;
        long groupId = 0;
        for (var event : book.events()) {
            if (previous == null || previous.tick() != event.tick() || previous.worldTick() != event.worldTick()) {
                if (!selected.isEmpty()) result.add(new TickGroup(groupId, selected.reversed()));
                selected = new ArrayList<>(); groupId = event.sequence();
            }
            if (book.matches(event, flag, tag)) selected.add(event);
            previous = event;
        }
        if (!selected.isEmpty()) result.add(new TickGroup(groupId, selected.reversed()));
        return List.copyOf(result.reversed());
    }
}
