package io.github.ruok0214.feedbackflag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class BookStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    private final Path file;
    public BookStorage(Path directory, String worldIdentity) { file = directory.resolve(key(worldIdentity) + ".json"); }
    public Path file() { return file; }
    public FlagBook load() throws IOException {
        if (!Files.exists(file)) return new FlagBook();
        if (Files.size(file) > MAX_BYTES) throw new IOException("History file exceeds 16 MiB: " + file);
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return FlagBook.restore(GSON.fromJson(reader, FlagBook.Data.class));
        } catch (RuntimeException ex) { throw new IOException("Invalid history: " + file, ex); }
    }
    public void save(FlagBook book) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "feedback-flag-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(book.data()), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String key(String identity) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
