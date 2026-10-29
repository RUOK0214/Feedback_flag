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
        recording(); pausesAndFilters(); observationGaps(); retention(); storage(); memos(); resetsAndTicks();
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
        revision = book.revision(); book.clearHistory(null);
        check(book.revision() == revision, "empty reset is harmless");
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
