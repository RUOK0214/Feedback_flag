package io.github.ruok0214.feedbackflag;

import java.util.List;

/** A page of newest-first events. Legacy clocks or a rolled-back world use receipt order. */
public record TimelineScale(boolean worldTicks, long start, long end) {
    public static TimelineScale of(List<FlagBook.Event> events) {
        if (events.isEmpty()) return new TimelineScale(true, 0, 0);
        boolean comparable = true;
        for (int i = 0; i < events.size(); i++) {
            if (!events.get(i).worldTick() || (i > 0 && events.get(i).tick() > events.get(i - 1).tick())) {
                comparable = false; break;
            }
        }
        var newest = events.getFirst(); var oldest = events.getLast();
        return comparable ? new TimelineScale(true, oldest.tick(), newest.tick())
                : new TimelineScale(false, oldest.sequence(), newest.sequence());
    }
    public double position(FlagBook.Event event) {
        if (start == end) return .5;
        double value = worldTicks ? event.tick() : event.sequence();
        return Math.clamp((value - start) / ((double)end - start), 0, 1);
    }
}
