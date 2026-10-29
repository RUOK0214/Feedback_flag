package io.github.ruok0214.feedbackflag;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FeedbackFlagClient implements ClientModInitializer {
    public static FlagManager manager;
    public static KeyMapping openMenu, placeFlag;
    private boolean menuRequested, addRequested, placementRequested;
    private record Marker(AABB pole, AABB cloth, AABB block, Vec3 labelPos, String label, int color, boolean pulse) { }
    private volatile List<Marker> markers = List.of();

    @Override public void onInitializeClient() {
        manager = new FlagManager();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("feedback_flag", "main"));
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.feedback_flag.menu",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        placeFlag = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.feedback_flag.place",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            var root = dispatcher.register(ClientCommands.literal("feedbackflag")
                    .requires(source -> source.attended())
                    .executes(context -> { menuRequested = true; return 1; })
                    .then(ClientCommands.literal("add").executes(context -> { addRequested = true; return 1; }))
                    .then(ClientCommands.literal("place").executes(context -> { placementRequested = true; return 1; })));
            dispatcher.register(ClientCommands.literal("ff").requires(source -> source.attended())
                    .executes(context -> { menuRequested = true; return 1; }).redirect(root));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            manager.tick(client);
            while (openMenu.consumeClick()) if (client.gui.screen() == null) menuRequested = true;
            while (placeFlag.consumeClick()) if (client.gui.screen() == null) placementRequested = true;
            if (client.level != null && manager.book() != null) {
                if (menuRequested) client.gui.setScreen(new DashboardScreen(manager, null));
                else if (addRequested) {
                    var flag = manager.addAtCrosshair();
                    if (flag != null) manager.openFlag(flag.id());
                } else if (placementRequested) manager.startPlacing();
            } else markers = List.of();
            menuRequested = addRequested = placementRequested = false;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.saveAll());
        LevelExtractionEvents.END_EXTRACTION.register(context -> markers = extractMarkers());
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (var marker : markers) {
                    Gizmos.cuboid(marker.pole(), GizmoStyle.fill(marker.color()));
                    Gizmos.cuboid(marker.cloth(), GizmoStyle.strokeAndFill(marker.color(), 1.5f, marker.color()));
                    if (marker.pulse()) Gizmos.cuboid(marker.block(), GizmoStyle.stroke(marker.color(), 3));
                    if (!marker.label().isBlank()) Gizmos.billboardText(marker.label(), marker.labelPos(),
                            TextGizmo.Style.forColorAndCentered(marker.color()).withScale(.25f));
                }
            }
        });
    }
    private List<Marker> extractMarkers() {
        Minecraft mc = Minecraft.getInstance();
        var book = manager.book();
        if (mc.level == null || mc.player == null || book == null || mc.gui.hud.isHidden()) return List.of();
        int alpha = Math.round(255 * (100 - book.transparency()) / 100f);
        if (alpha == 0) return List.of();
        List<Marker> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var flag : book.flags()) {
            var t = flag.target();
            if (!t.dimension().equals(manager.dimension()) || !mc.level.hasChunkAt(FlagManager.pos(t))
                    || mc.player.distanceToSqr(t.x() + .5, t.y() + 1, t.z() + .5) > 96 * 96) continue;
            boolean active = book.enabled() && flag.enabled();
            int color = ((active ? alpha : Math.max(1, alpha / 3)) << 24) | flag.color();
            result.add(new Marker(new AABB(t.x() + .48, t.y() + 1, t.z() + .48, t.x() + .52, t.y() + 1.85, t.z() + .52),
                    new AABB(t.x() + .52, t.y() + 1.48, t.z() + .48, t.x() + 1.05, t.y() + 1.82, t.z() + .52),
                    new AABB(FlagManager.pos(t)).inflate(.004), new Vec3(t.x() + .5, t.y() + 2.04, t.z() + .5),
                    flag.text(), color, active && now - flag.lastTime() >= 0 && now - flag.lastTime() < 1000));
        }
        return List.copyOf(result);
    }
}
