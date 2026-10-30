package io.github.ruok0214.feedbackflag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FlagBookTest {
    private static int checks;
    private static final FlagBook.Target TARGET = new FlagBook.Target("minecraft:overworld", 12, 64, -9);
    private static final FlagBook.Snapshot OFF = new FlagBook.Snapshot("minecraft:lever", Map.of("powered", "false", "face", "floor"));
    private static final FlagBook.Snapshot ON = new FlagBook.Snapshot("minecraft:lever", Map.of("powered", "true", "face", "floor"));
    private static final FlagBook.Snapshot AIR = new FlagBook.Snapshot("minecraft:air", Map.of());
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        recording(); pausesAndFilters(); observationGaps(); retention(); storage(); memos(); resetsAndTicks(); relativeGroupsAndTags(); visualSettingsAndRemoval(); searches(); undoDeletion();
        bulkEditing(); timelineZoom(); archives();
        System.out.println("Feedback Flag: " + checks + " assertions passed.");
    }
    private static void recording() {
        var book = new FlagBook(); var flag = book.add(TARGET, OFF, "레버 A");
        check(book.add(TARGET, OFF, "duplicate").id().equals(flag.id()), "one flag per target");
        check(book.observe(TARGET, OFF, 100, 1) == null, "identical packet is ignored");
        var first = book.observe(TARGET, ON, 200, 5);
        var second = book.observe(TARGET, OFF, 200, 5);
        check(first.kind() == FlagBook.Kind.STATE && first.before().equals(OFF) && first.after().equals(ON), "before and after states");
        check(second.sequence() == first.sequence() + 1 && book.events().size() == 2, "multiple transitions in the same tick");
        var removed = book.observe(TARGET, AIR, 300, 6);
        check(removed.kind() == FlagBook.Kind.BLOCK, "block removal");
        book.edit(flag.id(), "새 이름", 0xEF758C, true, true, true);
        check(book.flag(flag.id()).total() == 3 && book.flag(flag.id()).lastTime() == 300, "editing preserves activity counters");
        check(first.text().equals("레버 A") && first.color() == 0x56C8E8, "history metadata is immutable");
        book.remove(flag.id());
        check(book.flags().isEmpty() && book.events().size() == 3, "removing a flag retains history");
        check(book.observe(TARGET, ON, 400, 7) == null, "removed targets cannot record");
        check(!book.add(TARGET, ON, "re-added").id().equals(flag.id()), "re-added target has new identity");
    }
    private static void pausesAndFilters() {
        var book = new FlagBook(); var flag = book.add(TARGET, OFF, "Filter");
        book.setEnabled(false);
        check(book.observe(TARGET, ON, 100, 1) == null, "global pause");
        book.setEnabled(true);
        check(book.observe(TARGET, ON, 200, 2) == null, "global resume does not catch up");
        book.edit(flag.id(), "Filter", flag.color(), false, true, true);
        check(book.observe(TARGET, OFF, 300, 3) == null, "individual pause");
        book.edit(flag.id(), "Filter", flag.color(), true, true, true);
        check(book.observe(TARGET, OFF, 400, 4) == null, "individual resume does not catch up");
        book.edit(flag.id(), "Filter", flag.color(), true, true, false);
        check(book.observe(TARGET, ON, 500, 5) == null, "state filter");
        check(book.observe(TARGET, AIR, 600, 6) != null, "block changes still recorded");
        book.edit(flag.id(), "Filter", flag.color(), true, false, true);
        check(book.observe(TARGET, OFF, 700, 7) == null, "block filter");
        check(book.observe(TARGET, ON, 800, 8).before().equals(OFF), "filtered change advances baseline");
        book.setTransparency(200); check(book.transparency() == 100, "upper transparency bound");
        book.setTransparency(-20); check(book.transparency() == 0, "lower transparency bound");
        book.setTransparency(100);
        check(book.observe(TARGET, OFF, 900, 9) != null, "invisible flags still record");
    }
    private static void observationGaps() {
        var book = new FlagBook(); book.add(TARGET, OFF, "Gap");
        book.forget(TARGET);
        check(book.observe(TARGET, ON, 100, 1) == null, "first observation after unload is a baseline");
        check(book.observe(TARGET, OFF, 200, 2) != null, "observation resumes after baseline");
        var nether = new FlagBook.Target("minecraft:the_nether", 12, 64, -9);
        book.add(nether, OFF, "Nether");
        book.observe(nether, ON, 300, 3);
        check(book.baseline(TARGET).equals(OFF), "dimensions isolate baselines");
        book.forgetAll();
        check(book.observe(TARGET, ON, 400, 4) == null && book.observe(nether, OFF, 400, 4) == null, "world reconnect resets baselines");
    }
    private static void retention() {
        var book = new FlagBook(); var flag = book.add(TARGET, OFF, "Retention");
        for (int i = 1; i <= FlagBook.MAX_EVENTS + 5; i++) book.observe(TARGET, i % 2 == 0 ? OFF : ON, i, i);
        check(book.events().size() == FlagBook.MAX_EVENTS && book.events().getFirst().sequence() == 6, "bounded chronological retention");
        check(book.flag(flag.id()).total() == FlagBook.MAX_EVENTS + 5, "lifetime count survives pruning");
        for (int i = 1; i < FlagBook.MAX_FLAGS; i++) book.add(new FlagBook.Target("minecraft:overworld", i, 0, 0), OFF, "F");
        try { book.add(new FlagBook.Target("minecraft:overworld", 1000, 0, 0), OFF, "Over limit"); throw new AssertionError("flag cap missing"); }
        catch (IllegalStateException expected) { checks++; }
        check(FlagBook.cleanText("깃발😀".repeat(40)).codePointCount(0, FlagBook.cleanText("깃발😀".repeat(40)).length()) == 32, "Unicode label limit");
        check(FlagBook.cleanText("  A\nB§C  ").equals("ABC"), "unsafe label characters removed");
    }
    private static void memos() throws Exception {
        var book = new FlagBook(); var first = book.add(TARGET, OFF, "이름 A");
        var second = book.add(new FlagBook.Target("minecraft:overworld", 99, 64, 0), OFF, "이름 B");
        String memo = "입력 회로 😀\n\n리셋 전에 확인\n";
        check(first.memo().isEmpty(), "new flags start with an empty memo");
        long revision = book.revision();
        book.edit(first.id(), first.text(), memo, first.color(), true, true, true);
        check(book.revision() > revision && book.flag(first.id()).memo().equals(memo), "memo edits mark the book dirty");
        check(book.flag(first.id()).text().equals("이름 A") && book.flag(second.id()).memo().isEmpty(), "memo is independent of name and other flags");
        book.observe(TARGET, ON, 1234, 1);
        check(book.flag(first.id()).memo().equals(memo) && book.flag(first.id()).total() == 1, "block changes retain the memo");
        book.edit(first.id(), "이름 변경", 0xFFFFFF, false, false, false);
        check(book.flag(first.id()).memo().equals(memo), "renaming and toggling preserve the memo");
        book.edit(second.id(), second.text(), "다른 플래그 메모", second.color(), true, true, true);
        Path directory = Files.createTempDirectory(Path.of("build"), "memo-tests-");
        var storage = new BookStorage(directory, "memo-world"); storage.save(book);
        var restored = storage.load();
        check(restored.data().equals(book.data()), "multiline Unicode memo survives restart");
        check(restored.flag(second.id()).memo().equals("다른 플래그 메모"), "per-flag memos remain isolated after reload");
        // The previous release's JSON has no memo property. Missing and null fields both migrate to empty.
        var json = com.google.gson.JsonParser.parseString(Files.readString(storage.file())).getAsJsonObject();
        json.getAsJsonArray("flags").get(0).getAsJsonObject().remove("memo");
        json.getAsJsonArray("flags").get(1).getAsJsonObject().add("memo", com.google.gson.JsonNull.INSTANCE);
        Files.writeString(storage.file(), json.toString());
        var legacy = storage.load();
        check(legacy.flag(first.id()).memo().isEmpty() && legacy.flag(second.id()).memo().isEmpty(), "old and null memo fields default to empty");
        check(legacy.events().equals(book.events()) && legacy.flag(first.id()).text().equals("이름 변경"), "legacy migration preserves flags and history");
        book.edit(first.id(), "이름 변경", "", first.color(), true, true, true);
        storage.save(book);
        check(storage.load().flag(first.id()).memo().isEmpty(), "clearing a memo persists");
        check(book.flag(second.id()).memo().equals("다른 플래그 메모"), "clearing one memo does not touch another");
        check(FlagBook.cleanMemo("A\r\n\r\nB\rC\tD\u0000§").equals("A\n\nB\nC    D"), "memo normalizes line endings and removes controls");
        String longMemo = FlagBook.cleanMemo("😀".repeat(FlagBook.MAX_MEMO + 1));
        check(longMemo.codePointCount(0, longMemo.length()) == FlagBook.MAX_MEMO && longMemo.endsWith("😀"), "memo limit does not split Unicode characters");
    }
    private static void resetsAndTicks() throws Exception {
        var book = new FlagBook(); var a = book.add(TARGET, OFF, "A");
        var other = new FlagBook.Target("minecraft:overworld", 20, 64, 0);
        var b = book.add(other, OFF, "B");
        book.edit(a.id(), "A", "keep memo", 0x123456, true, true, false);
        book.observe(TARGET, AIR, 9000, 100);
        book.observe(other, ON, 8000, 110);
        book.observe(other, OFF, 1000, 130);
        var scale = TimelineScale.of(book.events().reversed());
        check(scale.worldTicks() && scale.start() == 100 && scale.end() == 130, "timeline uses game ticks rather than wall clock");
        check(Math.abs(scale.position(book.events().get(1)) - 1d / 3) < .00001, "tick differences set proportional timeline spacing");
        book.observe(other, ON, 99999, 130);
        check(TimelineScale.of(book.events().reversed()).position(book.events().getLast()) == 1, "same-tick transitions share a point but retain their rows");
        var sameTick = TimelineScale.of(book.events().subList(2, 4).reversed());
        check(sameTick.position(book.events().getLast()) == .5, "single-tick page is centered");
        book.observe(other, OFF, 100000, 20);
        check(!TimelineScale.of(book.events().reversed()).worldTicks(), "rolled-back game clock falls back to receipt order");
        book.setTransparency(42); book.setEnabled(false);
        long revision = book.revision(), sequence = book.data().sequence();
        var originalB = book.flag(b.id());
        book.clearHistory(a.id());
        check(book.events().size() == 4 && book.events().stream().allMatch(e -> e.flagId().equals(b.id())), "selected reset preserves other records");
        var resetA = book.flag(a.id());
        check(resetA.total() == 0 && resetA.lastTime() == 0 && book.flag(b.id()).equals(originalB), "selected reset clears only its counters");
        check(resetA.memo().equals("keep memo") && resetA.color() == 0x123456 && !resetA.watchProperties(), "reset retains memo and settings");
        check(!book.enabled() && book.transparency() == 42 && book.baseline(TARGET).equals(AIR), "reset preserves global settings and baseline");
        check(book.revision() > revision, "reset is marked for persistence");
        var storage = new BookStorage(Files.createTempDirectory(Path.of("build"), "reset-tests-"), "reset-world");
        storage.save(book); check(storage.load().data().equals(book.data()), "selective reset persists across restart");
        book.clearHistory(null);
        check(book.events().isEmpty() && book.flags().stream().allMatch(f -> f.total() == 0 && f.lastTime() == 0), "global reset clears history and all counters");
        check(book.flags().size() == 2 && book.flag(a.id()).memo().equals("keep memo"), "global reset preserves flags and memos");
        book.setEnabled(true);
        check(book.observe(other, OFF, 100001, 21) == null, "reset does not invent a transition");
        var next = book.observe(other, ON, 100002, 22);
        check(next.sequence() == sequence + 1 && book.flag(b.id()).total() == 1, "recording resumes with unique sequence and fresh count");
        book.remove(b.id()); book.clearHistory(b.id());
        check(book.events().isEmpty(), "reset can remove a deleted flag's history");
        storage.save(book); check(storage.load().events().isEmpty(), "empty reset persists");
        book.clearHistory(null); // Also clear the now-empty timeline's retained origin.
        revision = book.revision(); book.clearHistory(null);
        check(book.revision() == revision, "repeated empty reset is harmless");
        book.observe(TARGET, OFF, 200000, 300);
        storage.save(book);
        var json = com.google.gson.JsonParser.parseString(Files.readString(storage.file())).getAsJsonObject();
        json.addProperty("version", 1);
        json.getAsJsonArray("events").get(0).getAsJsonObject().remove("worldTick");
        Files.writeString(storage.file(), json.toString());
        var legacy = storage.load();
        check(!legacy.events().getFirst().worldTick() && legacy.events().getFirst().tick() == 300, "old tick values retain their original clock provenance");
        check(!TimelineScale.of(legacy.events().reversed()).worldTicks(), "legacy ticks do not pretend to share the world clock");
        storage.save(legacy); check(storage.load().data().equals(legacy.data()), "legacy clock provenance survives migration");
    }
    private static void relativeGroupsAndTags() throws Exception {
        var book = new FlagBook(); var a = book.add(TARGET, OFF, "Input");
        var other = new FlagBook.Target("minecraft:overworld", 50, 64, 0);
        var b = book.add(other, OFF, "Output");
        check(a.tags().isEmpty() && book.displayMode() == FlagBook.DisplayMode.FLAGS, "new flags have no tags and default to flag display");
        book.edit(a.id(), a.text(), "memo", FlagBook.parseTags(" Input, 회로 A, input, , 회로 A "), a.color(), true, true, true);
        book.edit(b.id(), b.text(), "", List.of("Output", "회로 A"), b.color(), true, true, true);
        check(book.flag(a.id()).tags().equals(List.of("Input", "회로 A")), "tags trim whitespace and deduplicate without case sensitivity");
        check(FlagBook.validTags("😀".repeat(24)) && !FlagBook.validTags("😀".repeat(25)), "tag limit counts Unicode code points");
        check(!FlagBook.validTags("1,2,3,4,5,6,7,8,9") && FlagBook.validTags("1,2,3,4,5,6,7,8,8"), "tag count limit applies to unique tags");
        var first = book.observe(TARGET, ON, 100000, 9000);
        var simultaneous = book.observe(other, ON, 200000, 9000);
        book.observe(TARGET, OFF, 300000, 9000);
        var later = book.observe(other, OFF, 0, 9020);
        check(book.relativeTick(first) == 0 && book.relativeTick(simultaneous) == 0 && book.relativeTick(later) == 20, "first shared change defines zero despite wall time differences");
        var groups = TickGroup.collect(book, null, null);
        check(groups.size() == 2 && groups.getLast().events().size() == 3, "same tick is one expandable group");
        check(groups.getLast().flags().size() == 2, "repeated transitions count one flag for the others label and color chips");
        check(groups.getLast().events().getFirst().sequence() == 3, "all same-tick transitions remain accessible newest first");
        var filtered = TickGroup.collect(book, null, "output");
        check(filtered.size() == 2 && filtered.getLast().id() == groups.getLast().id(), "filter retains original group identity");
        check(book.relativeTick(filtered.getFirst().representative()) == 20, "tag filtering does not rebase time");
        check(TickGroup.collect(book, a.id(), "Output").isEmpty(), "tag and selected flag filters intersect");
        check(TickGroup.collect(book, null, "").isEmpty(), "untagged filter excludes tagged flags");
        book.edit(a.id(), "renamed", a.color(), false, true, true);
        check(book.flag(a.id()).tags().equals(List.of("Input", "회로 A")), "name and recording edits preserve tags");
        book.edit(a.id(), "renamed", "memo", List.of("Retagged"), a.color(), true, true, true);
        check(TickGroup.collect(book, null, "Input").isEmpty() && TickGroup.collect(book, null, "Retagged").getFirst().events().size() == 2, "new tags apply to existing history");
        book.remove(a.id());
        check(TickGroup.collect(book, null, "Retagged").getFirst().events().size() == 2, "deleted flags retain their final tags in history");
        check(book.availableTags().contains("Retagged") && !book.availableTags().contains("Input"), "tag options include deleted historical flags without obsolete live tags");
        book.setDisplayMode(FlagBook.DisplayMode.BLOCKS);
        check(!book.displayMode().flags() && book.displayMode().blocks(), "block-only mode hides markers and shows bounds");
        book.setDisplayMode(book.displayMode().next());
        check(book.displayMode().flags() && book.displayMode().blocks(), "both mode shows markers and bounds");
        check(book.displayMode().next() == FlagBook.DisplayMode.FLAGS, "display mode cycles through three choices");
        var storage = new BookStorage(Files.createTempDirectory(Path.of("build"), "group-tests-"), "groups");
        storage.save(book); var restored = storage.load();
        check(restored.data().equals(book.data()) && restored.relativeTick(later) == 20, "origin, tags and display preference survive restart");
        var json = com.google.gson.JsonParser.parseString(Files.readString(storage.file())).getAsJsonObject();
        json.addProperty("version", 2); json.remove("displayMode"); json.remove("tickOrigin"); json.remove("legacyTickOrigin");
        for (var flag : json.getAsJsonArray("flags")) flag.getAsJsonObject().remove("tags");
        for (var event : json.getAsJsonArray("events")) event.getAsJsonObject().remove("tags");
        Files.writeString(storage.file(), json.toString()); var legacy = storage.load();
        check(legacy.displayMode() == FlagBook.DisplayMode.FLAGS && legacy.flags().getFirst().tags().isEmpty(), "old saves migrate to flags and empty tags");
        check(legacy.relativeTick(legacy.events().getFirst()) == 0 && legacy.relativeTick(legacy.events().getLast()) == 20, "old world-tick history derives its first relative origin");
        book.clearHistory(b.id());
        check(book.relativeTick(first) == 0, "individual clear retains shared origin");
        book.observe(other, ON, 400000, 9030);
        check(book.relativeTick(book.events().getLast()) == 30, "clearing one flag does not shift later comparisons");
        for (int i = 1; i <= FlagBook.MAX_EVENTS + 1; i++) book.observe(other, i % 2 == 0 ? ON : OFF, i, 9030 + i);
        check(book.relativeTick(book.events().getFirst()) > 0, "automatic pruning does not move zero");
        storage.save(book); check(storage.load().data().tickOrigin().equals(9000L), "pruned origin survives restart");
        book.clearHistory(null);
        var afterReset = book.observe(other, ON, 800000, 20000);
        check(book.relativeTick(afterReset) == 0 && book.displayMode() == FlagBook.DisplayMode.BOTH && book.flag(b.id()).tags().contains("Output"), "full reset starts a new zero and preserves tags and display");
        book.observe(other, OFF, 800001, 19900);
        book.observe(other, ON, 800002, 20000);
        check(TickGroup.collect(book, null, null).size() == 3, "clock rollback never merges separate periods with the same tick number");
        check(!TimelineScale.of(book.events().reversed()).worldTicks(), "clock rollback uses receipt order for the axis");
    }
    private static void visualSettingsAndRemoval() throws Exception {
        var book = new FlagBook(); var first = book.add(TARGET, OFF, "First");
        var nether = new FlagBook.Target("minecraft:the_nether", 12, 64, -9);
        var second = book.add(nether, OFF, "Second");
        check(book.visuals().equals(VisualSettings.DEFAULT), "old geometry and outline settings are the default");
        var visuals = new VisualSettings(4.5, 1.75, .6, .3, -.4, false, false);
        long revision = book.revision(); book.setVisuals(visuals);
        check(book.revision() > revision && book.visuals().equals(visuals), "appearance changes are persisted");
        revision = book.revision(); book.setVisuals(visuals);
        check(book.revision() == revision, "identical appearance does not dirty the book");
        var bounded = new VisualSettings(999, -20, -10, 9, 10, true, true);
        check(bounded.outlineWidth() == 8 && bounded.flagScale() == .25 && bounded.offsetX() == -2
                && bounded.offsetY() == 3 && bounded.offsetZ() == 2, "appearance input stays within supported bounds");
        var invalid = new VisualSettings(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN, true, true);
        check(invalid.equals(VisualSettings.DEFAULT), "nonfinite geometry input falls back safely");
        book.edit(first.id(), first.text(), "memo", List.of("final tag"), first.color(), true, true, true);
        book.observe(TARGET, ON, 100, 1000); book.observe(nether, ON, 101, 1005);
        book.setDisplayMode(FlagBook.DisplayMode.BOTH); book.setTransparency(60);
        var storage = new BookStorage(Files.createTempDirectory(Path.of("build"), "appearance-tests-"), "world");
        storage.save(book); check(storage.load().data().equals(book.data()), "all appearance settings survive restart");
        var json = com.google.gson.JsonParser.parseString(Files.readString(storage.file())).getAsJsonObject();
        json.addProperty("version", 3); json.remove("visuals");
        Files.writeString(storage.file(), json.toString()); var legacy = storage.load();
        check(legacy.visuals().equals(VisualSettings.DEFAULT) && legacy.flags().equals(book.flags())
                && legacy.events().equals(book.events()), "0.1.3 histories load with default appearance and intact data");
        book.remove(first.id()); // A previously removed flag's records must also stay.
        book.edit(second.id(), second.text(), "memo B", List.of("later tag"), second.color(), true, true, true);
        int historySize = book.events().size(); long sequence = book.data().sequence(); Long origin = book.data().tickOrigin();
        revision = book.revision(); book.removeAllFlags();
        check(book.flags().isEmpty() && book.at(nether) == null && book.baseline(nether) == null, "remove-all clears flags, lookup and baselines across dimensions");
        check(book.events().size() == historySize && book.data().sequence() == sequence && book.data().tickOrigin().equals(origin), "remove-all retains history and relative origin");
        check(book.events().getFirst().tags().equals(List.of("final tag")) && book.events().getLast().tags().equals(List.of("later tag")), "remove-all preserves final tags including already deleted flags");
        check(book.visuals().equals(visuals) && book.displayMode() == FlagBook.DisplayMode.BOTH && book.transparency() == 60, "remove-all preserves world preferences");
        check(book.revision() > revision && book.observe(nether, OFF, 102, 1006) == null, "deleted targets stop observing and removal marks storage dirty");
        storage.save(book); check(storage.load().data().equals(book.data()), "remove-all survives restart without losing history");
        revision = book.revision(); book.removeAllFlags();
        check(book.revision() == revision, "remove-all with no live flags is harmless");
        var readded = book.add(nether, ON, "New");
        check(!readded.id().equals(second.id()) && book.observe(nether, ON, 103, 1007) == null, "readding after remove-all starts a fresh flag without invented changes");
        book.clearHistory(null);
        check(book.visuals().equals(visuals) && book.flags().size() == 1, "timeline reset leaves appearance and live flags alone");
    }
    private static void searches() {
        var book = new FlagBook(); var flag = book.add(TARGET, OFF, "회로 Signal A");
        book.edit(flag.id(), flag.text(), "memo is not a name", List.of("입력", "Circuit A"), 0x2489C7, true, true, true);
        flag = book.flag(flag.id()); var names = List.of("light_blue", "light blue", "하늘");
        check(new FlagSearch("회로 signal", "하늘", "입력, CIRCUIT", FlagSearch.Activation.ENABLED).matches(flag, names), "all search criteria intersect with Korean and case folding");
        check(new FlagSearch("", "#2489c7", "", null).matches(flag, names), "custom hex search is case insensitive");
        check(new FlagSearch("", "2489C7", "", null).matches(flag, List.of()), "hex searches custom colors without palette names");
        check(new FlagSearch("", "light b", "", null).matches(flag, names), "partial English palette name");
        check(!new FlagSearch("", "#2489C8", "", null).matches(flag, names), "different RGB excluded");
        check(!new FlagSearch("", "#248", "", null).matches(flag, names), "incomplete RGB yields no results");
        check(!new FlagSearch("memo", "", "", null).matches(flag, names), "name search does not accidentally search memos");
        check(!new FlagSearch("", "", "입력, 출력", null).matches(flag, names), "each comma separated tag must match");
        check(!new FlagSearch("", "", "", FlagSearch.Activation.DISABLED).matches(flag, names), "inactive filter excludes active flags");
        book.setEnabled(false);
        check(new FlagSearch("", "", "", FlagSearch.Activation.ENABLED).matches(flag, names), "global pause does not change individual activation search");
        book.edit(flag.id(), flag.text(), flag.color(), false, true, true);
        check(new FlagSearch(null, null, null, FlagSearch.Activation.DISABLED).matches(book.flag(flag.id()), names), "disabled filter matches own switch");
        check(new FlagSearch("  ", "", ", ,", null).matches(flag, names), "empty criteria show all flags");
    }
    private static void undoDeletion() throws Exception {
        var book = new FlagBook(); var first = book.add(TARGET, OFF, "Restore me");
        check(book.undoStatus() == FlagBook.UndoStatus.EMPTY && !book.undoDeletion(), "fresh book has no undo");
        book.edit(first.id(), first.text(), "메모\n두 줄", List.of("입력"), 0x123456, true, false, true);
        var event = book.observe(TARGET, ON, 42, 300); first = book.flag(first.id());
        var storage = new BookStorage(Files.createTempDirectory(Path.of("build"), "undo-tests-"), "world");
        book.remove(first.id()); long revision = book.revision();
        book.remove(first.id()); book.removeAllFlags();
        check(book.revision() == revision && book.deletedCount() == 1, "no-op deletion keeps pending undo");
        storage.save(book); book = storage.load();
        check(book.deletedCount() == 1 && book.undoStatus() == FlagBook.UndoStatus.READY, "deletion survives restart");
        check(book.undoDeletion() && book.flag(first.id()).equals(first), "single undo restores UUID, position, text, memo, tags, settings and counters");
        check(book.events().size() == 1 && book.relativeTick(event) == 0 && book.baseline(TARGET) == null, "undo keeps history and origin without restoring stale baseline");
        check(book.observe(TARGET, OFF, 43, 301) == null && book.observe(TARGET, ON, 44, 302) != null, "undo observes a fresh baseline and records only later changes");
        check(!book.undoDeletion() && book.deletedCount() == 0, "undo can only be used once");
        var other = new FlagBook.Target("minecraft:the_nether", 1, 2, 3);
        book.add(other, OFF, "Other dimension"); var originals = book.flags();
        book.setEnabled(false); book.setTransparency(67); book.setDisplayMode(FlagBook.DisplayMode.BOTH);
        book.removeAllFlags(); storage.save(book); book = storage.load();
        var dataBeforeUndo = book.data();
        check(book.deletedCount() == 2 && book.undoDeletion() && book.flags().equals(originals), "bulk undo restores all dimensions atomically after restart");
        check(!book.enabled() && book.transparency() == 67 && book.displayMode() == FlagBook.DisplayMode.BOTH
                && book.data().tickOrigin().equals(dataBeforeUndo.tickOrigin()), "undo preserves current world settings and tick origin");
        book.removeAllFlags(); var replacement = book.add(TARGET, OFF, "New occupant"); var blocked = book.data();
        check(book.undoStatus() == FlagBook.UndoStatus.CONFLICT && !book.undoDeletion() && book.data().equals(blocked), "coordinate conflict preserves new flag and complete undo snapshot");
        storage.save(book); book = storage.load();
        check(book.undoStatus() == FlagBook.UndoStatus.CONFLICT && book.at(TARGET).id().equals(replacement.id()), "conflict stays safe across restart");
        book.remove(replacement.id());
        check(book.deletedCount() == 1 && book.undoDeletion() && book.flags().size() == 1, "next actual deletion replaces the previous undo slot");
        book = new FlagBook(); first = book.add(TARGET, OFF, "Deleted counter"); book.observe(TARGET, ON, 7, 8);
        book.remove(first.id()); book.clearHistory(null);
        check(book.undoDeletion() && book.flag(first.id()).total() == 0 && book.flag(first.id()).lastTime() == 0 && book.events().isEmpty(), "undo never restores counters erased by history reset");
        book.seed(TARGET, ON); var afterReset = book.observe(TARGET, OFF, 9, 10);
        check(book.relativeTick(afterReset) == 0, "history reset still establishes a new tick origin after undo");
        book.remove(first.id());
        for (int i = 0; i < FlagBook.MAX_FLAGS; i++) book.add(new FlagBook.Target("minecraft:overworld", i, 10, 0), OFF, "New " + i);
        var full = book.data();
        check(book.undoStatus() == FlagBook.UndoStatus.LIMIT && !book.undoDeletion() && book.data().equals(full), "undo respects capacity without partial restore");
        var invalid = new FlagBook.Data(5, true, 15, List.of(), List.of(), 0, null, null, null, null, List.of(first, first));
        try { FlagBook.restore(invalid); throw new AssertionError("duplicate undo accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        var legacy = FlagBook.restore(new FlagBook.Data(4, true, 15, List.of(first), List.of(), 0));
        check(legacy.deletedCount() == 0 && legacy.flags().equals(List.of(first)), "old saves load with an empty undo slot");
        check(new BookStorage(storage.file().getParent(), "other-world").load().deletedCount() == 0, "undo is isolated by world storage");
    }
    private static void bulkEditing() {
        var book = new FlagBook(); var a = book.add(TARGET, OFF, "A"); var b = book.add(new FlagBook.Target("minecraft:overworld", 4, 5, 6), ON, "B");
        book.edit(a.id(), "A", "memo A", List.of("Input"), 0x123456, true, true, false);
        book.edit(b.id(), "B", "memo B", List.of("Output"), 0x654321, false, false, true);
        book.observe(TARGET, AIR, 5, 9); var beforeA = book.flag(a.id()); var beforeB = book.flag(b.id());
        var keep = BulkEdit.Switch.KEEP;
        var edit = new BulkEdit(0xABCDEF, BulkEdit.Switch.ON, keep, keep, BulkEdit.Tags.ADD, List.of("Circuit", "input"));
        check(book.editAll(List.of(a.id(), b.id(), a.id()), edit) == 2, "bulk selection deduplicates identities");
        var afterA = book.flag(a.id()); var afterB = book.flag(b.id());
        check(afterA.color() == 0xABCDEF && afterB.color() == 0xABCDEF && afterB.enabled(), "bulk updates selected color and activation");
        check(!afterA.watchProperties() && !afterB.watchBlocks(), "KEEP preserves differing detection settings");
        check(afterA.memo().equals(beforeA.memo()) && afterB.text().equals(beforeB.text()) && afterA.total() == beforeA.total(), "bulk preserves names memos and counters");
        check(afterA.tags().equals(List.of("Input", "Circuit")) && afterB.tags().equals(List.of("Output", "Circuit", "input")), "bulk adds tags without replacing existing ones");
        check(book.baseline(TARGET).equals(AIR) && book.events().size() == 1, "bulk does not alter baselines or events");
        long revision = book.revision(); book.editAll(List.of(a.id()), new BulkEdit(null, keep, keep, keep, BulkEdit.Tags.KEEP, List.of()));
        check(book.revision() == revision, "all KEEP is a no-op");
        book.editAll(List.of(a.id()), new BulkEdit(null, keep, keep, keep, BulkEdit.Tags.REMOVE, List.of("INPUT")));
        check(book.flag(a.id()).tags().equals(List.of("Circuit")) && book.flag(b.id()).tags().contains("input"), "tag removal is exact case-insensitive and limited to selection");
        book.edit(b.id(), "B", "memo B", List.of("1", "2", "3", "4", "5", "6", "7", "8"), b.color(), false, false, true);
        var unchanged = book.data();
        try { book.editAll(List.of(a.id(), b.id()), new BulkEdit(0, keep, keep, keep, BulkEdit.Tags.ADD, List.of("ninth"))); throw new AssertionError("partial edit accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(book.data().equals(unchanged), "overflow in later flag rolls back the entire bulk edit");
        try { book.editAll(List.of(a.id(), java.util.UUID.randomUUID()), edit); throw new AssertionError("stale selection accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(book.data().equals(unchanged), "stale selection does not partially edit live flags");
        book.editAll(List.of(a.id(), b.id()), new BulkEdit(null, keep, keep, keep, BulkEdit.Tags.REPLACE, List.of()));
        check(book.flags().stream().allMatch(f -> f.tags().isEmpty()), "explicit empty replacement clears tags");
    }
    private static void timelineZoom() {
        var view = new TimelineViewport(); view.update(new TimelineScale(true, 100, 200));
        check(view.start() == 100 && view.end() == 200, "initial viewport fits all ticks");
        view.zoom(.5, .5); check(view.start() == 125 && view.end() == 175, "zoom halves visible tick interval around center");
        view.update(new TimelineScale(true, 100, 300));
        check(view.start() == 125 && view.end() == 175, "live arrivals keep inspected interval fixed");
        view.pan(-10); check(view.start() == 100 && view.end() == 150, "pan clamps at oldest event");
        view.latest(); check(view.start() == 250 && view.end() == 300, "latest preserves zoom span");
        view.update(new TimelineScale(true, 100, 320)); check(view.start() == 270 && view.end() == 320, "follow latest tracks arrivals");
        view.zoom(.5, 1); check(view.end() == 320 && view.start() == 295, "anchored zoom retains cursor position");
        view.reset(); check(view.start() == 100 && view.end() == 320, "fit all resets zoom");
        view.update(new TimelineScale(false, 1, 10)); check(!view.worldTicks() && view.start() == 1 && view.end() == 10, "clock rollback resets to receipt-order interval");
        view.update(new TimelineScale(true, 200, 200)); view.zoom(.5, .5); view.pan(1);
        check(view.start() == 200 && view.end() == 200 && !view.canZoomIn(), "same-tick timeline remains finite");
        view.update(new TimelineScale(true, 0, 0)); check(view.start() == 0 && view.end() == 0, "empty timeline safely resets bounds");
    }
    private static void archives() throws Exception {
        var book = new FlagBook(); var a = book.add(TARGET, OFF, "Input");
        book.observe(TARGET, ON, 100, 1000); var later = book.observe(TARGET, OFF, 101, 1020);
        book.edit(a.id(), "Saved name", "메모\n</script><img src=x onerror=alert(1)>", List.of("Final tag"), 0x123456, true, true, true);
        var archive = TimelineArchive.capture(book, List.of(later), "../실험 <script>", "World A");
        check(archive.book().events().size() == 1 && archive.book().relativeTick(later) == 20, "archive selection retains original zero tick");
        check(archive.book().events().getFirst().text().equals("Input") && archive.book().tags(later).equals(List.of("Final tag")), "archive preserves event name and save-time tags");
        book.edit(a.id(), "Changed", "new memo", List.of("New"), 0, false, false, false); book.clearHistory(null);
        check(archive.book().flag(a.id()).memo().startsWith("메모") && archive.book().events().size() == 1, "later edits and resets never change a capture");
        check(archive.data().deleted().isEmpty(), "archive never imports a deletion undo slot");
        Path root = Files.createTempDirectory(Path.of("build"), "archive-tests-"); var files = new TimelineFiles(root);
        var saved = files.save(archive); check(saved.getParent().equals(root.toAbsolutePath()), "archive titles cannot escape the save directory");
        check(files.read(saved).equals(archive), "archive JSON round trip");
        var html = Files.readString(saved.resolve("timeline.html"));
        check(html.contains("\\u003c/script\\u003e") && !html.contains("<img src=x"), "exported memo cannot escape JSON into executable HTML");
        check(!html.contains("__TIMELINE_DATA__") && html.contains("Final tag"), "HTML includes its own immutable event data");
        check(files.list().size() == 1 && files.list().getFirst().events() == 1, "library lists saved records with metadata");
        var another = files.save(archive); check(!another.equals(saved) && files.list().size() == 2, "same named saves never overwrite each other");
        var corrupt = Files.createDirectory(root.resolve("broken")); Files.writeString(corrupt.resolve("timeline.json"), "{broken");
        check(files.list().size() == 3 && files.list().stream().filter(TimelineFiles.Entry::valid).count() == 2, "one corrupt file does not hide other archives");
        try { files.read(corrupt); throw new AssertionError("corrupt archive accepted"); } catch (java.io.IOException expected) { checks++; }
        try { files.read(root.getParent()); throw new AssertionError("outside path accepted"); } catch (java.io.IOException expected) { checks++; }
        var detached = archive.book(); detached.clearHistory(null);
        check(archive.book().events().size() == 1, "archive viewing uses a detached book");
        try { new TimelineArchive("other-format", 1, "bad", "World", 0, archive.data()); throw new AssertionError("wrong format accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        var empty = TimelineArchive.capture(book, List.of(), "Empty", "World");
        check(empty.book().events().isEmpty() && TimelineFiles.html(empty).contains("\"events\": []"), "empty archive remains readable");
        var preview = Path.of("build", "html-preview"); Files.createDirectories(preview);
        Files.writeString(preview.resolve("timeline.html"), html);
    }
    private static void storage() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("build"), "flag-tests-");
        var storage = new BookStorage(directory, "server:localhost:25565");
        check(!BookStorage.key("server:a").equals(BookStorage.key("server:b")), "server isolation");
        check(!BookStorage.key("local:a").equals(BookStorage.key("server:a")), "local and remote isolation");
        check(storage.load().flags().isEmpty(), "missing file defaults");
        var book = new FlagBook(); var flag = book.add(TARGET, OFF, "테스트 😀");
        book.observe(TARGET, ON, 1234567, 33); book.setTransparency(76); book.setEnabled(false);
        storage.save(book); var restored = storage.load();
        check(restored.data().equals(book.data()), "JSON round trip preserves preferences, flags and events");
        check(restored.baseline(TARGET) == null, "baselines never persisted");
        restored.setEnabled(true); restored.seed(TARGET, ON);
        check(restored.observe(TARGET, OFF, 1234568, 1).sequence() == 2, "event sequence survives restart");
        storage.save(restored); check(storage.load().events().size() == 2, "atomic replacement");
        try (var files = Files.list(directory)) { check(files.noneMatch(p -> p.toString().endsWith(".tmp")), "temporary file cleanup"); }
        Files.writeString(storage.file(), "{broken", StandardCharsets.UTF_8);
        try { storage.load(); throw new AssertionError("corruption accepted"); }
        catch (java.io.IOException expected) { checks++; }
        check(Files.readString(storage.file()).equals("{broken"), "invalid source is not overwritten");
        Path invalidParent = directory.resolve("not-a-directory"); Files.writeString(invalidParent, "keep");
        try { new BookStorage(invalidParent, "fail").save(book); throw new AssertionError("write failure swallowed"); }
        catch (java.io.IOException expected) { checks++; }
        check(Files.readString(invalidParent).equals("keep"), "failed save preserves unrelated file");
        try { FlagBook.restore(new FlagBook.Data(99, true, 15, List.of(), List.of(), 0)); throw new AssertionError("unknown schema accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { FlagBook.restore(new FlagBook.Data(1, true, 15, List.of(flag, flag), List.of(), 0)); throw new AssertionError("duplicate id accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
