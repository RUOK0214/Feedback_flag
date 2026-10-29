package io.github.ruok0214.feedbackflag;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public final class FlagManager {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("feedback_flag");
    private static final class Session {
        final BookStorage storage;
        FlagBook book;
        long savedRevision;
        boolean readFailed, saveFailed;
        Session(String identity) {
            storage = new BookStorage(FabricLoader.getInstance().getConfigDir().resolve("feedback_flag/worlds"), identity);
            try { book = storage.load(); }
            catch (IOException ex) { LOG.error("Cannot load flags; preserving the original file", ex); book = new FlagBook(); readFailed = true; }
        }
    }
    private final Map<String, Session> sessions = new HashMap<>();
    private Session session;
    private ClientLevel level;
    private long ticks;
    private boolean placing;

    public FlagBook book() { return session == null ? null : session.book; }
    public boolean placing() { return placing; }
    public long ticks() { return ticks; }
    public String errorKey() {
        return session == null ? null : session.readFailed ? "load_failed" : session.saveFailed ? "save_failed" : null;
    }
    public String dimension() { return level == null ? "" : level.dimension().identifier().toString(); }
    private String identity(Minecraft mc) {
        if (mc.getSingleplayerServer() != null)
            return "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (mc.getCurrentServer() != null) return "server:" + mc.getCurrentServer().ip.strip().toLowerCase(Locale.ROOT);
        // Direct/LAN connections without ServerData must not share the same storage bucket.
        return "connection:" + mc.getConnection().getConnection().getRemoteAddress();
    }
    public void tick(Minecraft mc) {
        ticks++;
        if (level != mc.level) {
            save();
            if (book() != null) book().forgetAll();
            level = mc.level; placing = false;
            if (level == null) { session = null; return; }
            session = sessions.computeIfAbsent(identity(mc), Session::new);
            book().forgetAll();
        }
        if (level == null || book() == null) return;
        for (FlagBook.Flag flag : book().flags()) {
            if (!flag.target().dimension().equals(dimension())) continue;
            BlockPos pos = pos(flag.target());
            if (!level.hasChunkAt(pos)) book().forget(flag.target());
            else if (book().baseline(flag.target()) == null) book().seed(flag.target(), snapshot(level.getBlockState(pos)));
        }
        if (ticks % 100 == 0) save();
    }
    public boolean save() {
        if (session == null) return true;
        if (session.readFailed) return false;
        if (session.savedRevision == book().revision()) return true;
        try {
            session.storage.save(book()); session.savedRevision = book().revision(); session.saveFailed = false; return true;
        } catch (IOException ex) { session.saveFailed = true; LOG.error("Cannot save feedback flags", ex); return false; }
    }
    public void saveAll() {
        Session active = session;
        for (Session item : sessions.values()) { session = item; save(); }
        session = active;
    }
    /** Authoritative server block packets, including multiple transitions during the same client tick. */
    public void observed(ClientLevel source, BlockPos pos, BlockState state) {
        if (source != level || book() == null || !source.hasChunkAt(pos)) return;
        FlagBook.Target target = target(pos);
        if (book().at(target) != null) book().observe(target, snapshot(state), System.currentTimeMillis(), ticks);
    }
    public void unloaded(ClientLevel source, int chunkX, int chunkZ) {
        if (source != level || book() == null) return;
        for (var flag : book().flags()) {
            var target = flag.target();
            if (target.dimension().equals(dimension()) && target.x() >> 4 == chunkX && target.z() >> 4 == chunkZ)
                book().forget(target);
        }
    }
    public FlagBook.Target target(BlockPos pos) { return new FlagBook.Target(dimension(), pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos pos(FlagBook.Target target) { return new BlockPos(target.x(), target.y(), target.z()); }
    public static FlagBook.Snapshot snapshot(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        state.getValues().forEach(value -> properties.put(value.property().getName(), propertyValue(value.property(), value.value())));
        return new FlagBook.Snapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties);
    }
    private static <T extends Comparable<T>> String propertyValue(net.minecraft.world.level.block.state.properties.Property<T> property, Comparable<?> value) {
        @SuppressWarnings("unchecked") T cast = (T) value;
        return property.getName(cast);
    }
    public void startPlacing() {
        placing = !placing;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("feedback_flag." + (placing ? "place_hint" : "place_cancelled")));
    }
    public FlagBook.Flag addAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (book() == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            message("look_at_block"); return null;
        }
        return add(hit.getBlockPos());
    }
    private FlagBook.Flag add(BlockPos pos) {
        try {
            var flag = book().add(target(pos), snapshot(level.getBlockState(pos)), "Flag " + (book().flags().size() + 1));
            save(); return flag;
        } catch (IllegalStateException ex) { message("flag_limit"); return null; }
    }
    private void message(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(Component.translatable("feedback_flag." + key));
    }
    /** Consumes only deliberate flag interactions, before Minecraft sends a block-use packet. */
    public boolean use() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null || mc.gui.screen() != null || book() == null) return false;
        if (placing) {
            FlagBook.Flag added = addAtCrosshair();
            if (added != null) { placing = false; openFlag(added.id()); }
            return true;
        }
        FlagBook.Flag selected = hitFlag();
        if (selected == null && mc.player.isShiftKeyDown() && mc.player.getMainHandItem().isEmpty()
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            selected = book().at(target(hit.getBlockPos()));
        if (selected == null) return false;
        openFlag(selected.id()); return true;
    }
    public void openFlag(UUID id) { Minecraft.getInstance().gui.setScreen(new FlagEditScreen(new DashboardScreen(this, null), this, id)); }
    public FlagBook.Flag hitFlag() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || book().transparency() == 100) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1).scale(mc.player.blockInteractionRange()));
        double nearest = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS
                ? eye.distanceToSqr(end) : eye.distanceToSqr(mc.hitResult.getLocation()) + 0.01;
        FlagBook.Flag result = null;
        for (var flag : book().flags()) {
            if (!flag.target().dimension().equals(dimension()) || !level.hasChunkAt(pos(flag.target()))) continue;
            var t = flag.target();
            var hit = new AABB(t.x() + .45, t.y() + 1, t.z() + .43, t.x() + 1.08, t.y() + 1.85, t.z() + .57).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get()); result = flag;
            }
        }
        return result;
    }
    public String status(FlagBook.Flag flag) {
        if (!book().enabled() || !flag.enabled()) return "paused";
        if (!flag.target().dimension().equals(dimension()) || book().baseline(flag.target()) == null) return "unloaded";
        return "watching";
    }
}
