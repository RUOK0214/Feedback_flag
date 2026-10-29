package io.github.ruok0214.feedbackflag;

/** Per-world display preferences; changes never move the watched block coordinates. */
public record VisualSettings(double outlineWidth, double flagScale, double offsetX, double offsetY, double offsetZ,
                             boolean showNames, boolean throughWalls) {
    public static final VisualSettings DEFAULT = new VisualSettings(2, 1, 0, 0, 0, true, true);
    public enum Parameter {
        OUTLINE_WIDTH(1, 8, .5), FLAG_SCALE(.25, 3, .05),
        OFFSET_X(-2, 2, .05), OFFSET_Y(-1, 3, .05), OFFSET_Z(-2, 2, .05);
        public final double min, max, step;
        Parameter(double min, double max, double step) { this.min = min; this.max = max; this.step = step; }
    }
    public VisualSettings {
        outlineWidth = bounded(outlineWidth, 1, 8, 2);
        flagScale = bounded(flagScale, .25, 3, 1);
        offsetX = bounded(offsetX, -2, 2, 0);
        offsetY = bounded(offsetY, -1, 3, 0);
        offsetZ = bounded(offsetZ, -2, 2, 0);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    public double value(Parameter parameter) {
        return switch (parameter) {
            case OUTLINE_WIDTH -> outlineWidth; case FLAG_SCALE -> flagScale;
            case OFFSET_X -> offsetX; case OFFSET_Y -> offsetY; case OFFSET_Z -> offsetZ;
        };
    }
    public VisualSettings withValue(Parameter parameter, double value) {
        return new VisualSettings(parameter == Parameter.OUTLINE_WIDTH ? value : outlineWidth,
                parameter == Parameter.FLAG_SCALE ? value : flagScale, parameter == Parameter.OFFSET_X ? value : offsetX,
                parameter == Parameter.OFFSET_Y ? value : offsetY, parameter == Parameter.OFFSET_Z ? value : offsetZ,
                showNames, throughWalls);
    }
    public VisualSettings withVisibility(boolean names, boolean walls) {
        return new VisualSettings(outlineWidth, flagScale, offsetX, offsetY, offsetZ, names, walls);
    }
}
