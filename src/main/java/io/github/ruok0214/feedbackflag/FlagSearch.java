package io.github.ruok0214.feedbackflag;

import java.util.List;
import java.util.Locale;

/** All supplied fields must match. Activation refers to the flag's own recording switch. */
public record FlagSearch(String name, String color, String tags, Activation activation) {
    public enum Activation {
        ALL, ENABLED, DISABLED;
        public Activation next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public FlagSearch {
        name = normalize(name); color = normalize(color); tags = normalize(tags);
        if (activation == null) activation = Activation.ALL;
    }
    public boolean matches(FlagBook.Flag flag, List<String> colorNames) {
        if (!normalize(flag.text()).contains(name)) return false;
        if (activation != Activation.ALL && flag.enabled() != (activation == Activation.ENABLED)) return false;
        if (!color.isEmpty()) {
            if (color.matches("#?[0-9a-f]{6}")) {
                if (flag.color() != Integer.parseInt(color.replace("#", ""), 16)) return false;
            } else if (colorNames.stream().noneMatch(n -> normalize(n).contains(color))) return false;
        }
        for (String part : tags.split(",")) {
            String query = part.strip();
            if (!query.isEmpty() && flag.tags().stream().noneMatch(t -> normalize(t).contains(query))) return false;
        }
        return true;
    }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }
}
