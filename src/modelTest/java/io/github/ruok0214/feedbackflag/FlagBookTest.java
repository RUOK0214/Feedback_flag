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
        recording(); pausesAndFilters(); observationGaps(); retention(); storage();
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
        try { FlagBook.restore(new FlagBook.Data(2, true, 15, List.of(), List.of(), 0)); throw new AssertionError("unknown schema accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { FlagBook.restore(new FlagBook.Data(1, true, 15, List.of(flag, flag), List.of(), 0)); throw new AssertionError("duplicate id accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
