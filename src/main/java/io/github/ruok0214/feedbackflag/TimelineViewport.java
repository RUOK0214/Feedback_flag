package io.github.ruok0214.feedbackflag;

/** Shared zoom/pan rules. A manually inspected interval stays fixed as live events arrive. */
public final class TimelineViewport {
    private TimelineScale scale = new TimelineScale(true, 0, 0);
    private double start, end;
    private boolean fit = true, follow;
    public void update(TimelineScale next) {
        if (scale.worldTicks() != next.worldTicks()) fit = true;
        double span = end - start;
        scale = next;
        if (fit) { start = scale.start(); end = scale.end(); }
        else {
            if (follow) { end = scale.end(); start = end - span; }
            clamp();
        }
    }
    public void reset() { fit = true; follow = false; start = scale.start(); end = scale.end(); }
    public void latest() { fit = false; follow = true; double span = end - start; end = scale.end(); start = end - span; clamp(); }
    public void zoom(double factor, double anchor) {
        if (!Double.isFinite(factor) || factor <= 0 || scale.end() <= scale.start()) return;
        anchor = Math.clamp(anchor, 0, 1);
        double span = Math.clamp((end - start) * factor, 1, Math.max(1, (double)scale.end() - scale.start()));
        double point = start + anchor * (end - start);
        start = point - anchor * span; end = start + span; fit = follow = false; clamp();
    }
    public void pan(double direction) {
        if (!Double.isFinite(direction)) return;
        double offset = (end - start) * direction;
        start += offset; end += offset; fit = follow = false; clamp();
    }
    private void clamp() {
        double span = Math.min(end - start, (double)scale.end() - scale.start());
        start = Math.clamp(start, scale.start(), scale.end() - span); end = start + span;
    }
    public double value(FlagBook.Event event) { return scale.worldTicks() ? event.tick() : event.sequence(); }
    public boolean contains(FlagBook.Event event) { return value(event) >= start && value(event) <= end; }
    public double position(FlagBook.Event event) { return end == start ? .5 : (value(event) - start) / (end - start); }
    public double start() { return start; }
    public double end() { return end; }
    public boolean worldTicks() { return scale.worldTicks(); }
    public boolean canZoomIn() { return end - start > 1; }
    public boolean canZoomOut() { return end - start < (double)scale.end() - scale.start(); }
}
