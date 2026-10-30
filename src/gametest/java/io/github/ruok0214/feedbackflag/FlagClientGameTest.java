package io.github.ruok0214.feedbackflag;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/** Runs against a real integrated server, renderer and packet listener in an isolated test world. */
@SuppressWarnings("UnstableApiUsage")
public final class FlagClientGameTest implements FabricClientGameTest {
    private static final String MEMO = "회로 입력\n리셋 전 확인";
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void command(ClientGameTestContext context, String command) {
        context.getInput().pressKey(GLFW.GLFW_KEY_T);
        context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars("/" + command);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitTick();
    }
    private static void edit(ClientGameTestContext context, String name, String color) {
        context.waitForScreen(FlagEditScreen.class);
        context.runOnClient(mc -> {
            var boxes = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            boxes.get(0).setValue(name); boxes.get(1).setValue(color);
            boxes.get(2).setValue(name.equals("Signal A") ? "입력, 공통" : name.equals("Block B") ? "출력, 공통" : name.equals("Placed C") ? "출력" : "");
        });
        context.clickScreenButton("feedback_flag.save");
        context.waitForScreen(DashboardScreen.class);
    }
    private static void aim(ClientGameTestContext context, double x, double y, double z) {
        context.runOnClient(mc -> {
            var eye = mc.player.getEyePosition();
            double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx+dz*dz))));
        });
        context.waitTicks(3);
    }
    private static MultiLineEditBox memoEditor(net.minecraft.client.Minecraft mc) {
        return mc.gui.screen().children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
    }
    private static net.minecraft.client.gui.components.Button button(net.minecraft.client.Minecraft mc, String key) {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        return mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static String visibleLabel(ClientGameTestContext context, String prefix) {
        return context.computeOnClient(mc -> mc.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(b -> b.visible && b.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow().getMessage().getString());
    }
    private static void openTagFilter(ClientGameTestContext context) {
        context.clickScreenButton(visibleLabel(context, "태그:")); context.waitForScreen(TagFilterScreen.class);
    }
    private static void adjustSlider(ClientGameTestContext context, String prefix, int steps) {
        context.runOnClient(mc -> {
            var slider = mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.AbstractSliderButton.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractSliderButton.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
            mc.gui.screen().setFocused(slider);
            slider.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            for (int i = 0; i < Math.abs(steps); i++) slider.keyPressed(new net.minecraft.client.input.KeyEvent(steps > 0 ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT, 0, 0));
            slider.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        });
    }
    private static void testAppearance(ClientGameTestContext context) {
        context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
        context.clickScreenButton("feedback_flag.visual_settings"); context.waitForScreen(VisualSettingsScreen.class);
        adjustSlider(context, "윤곽선 두께:", 20); adjustSlider(context, "플래그 크기:", 20);
        adjustSlider(context, "위치 X", 20); adjustSlider(context, "높이 Y", 10); adjustSlider(context, "위치 Z", -10);
        context.clickScreenButton(visibleLabel(context, "이름 표시:"));
        context.clickScreenButton(visibleLabel(context, "가려진 윤곽선:"));
        context.clickScreenButton(visibleLabel(context, "표시:")); context.clickScreenButton(visibleLabel(context, "표시:"));
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().visuals().equals(VisualSettings.DEFAULT), "Appearance edits stay as a draft"));
        context.getInput().resizeWindow(1000, 700); context.waitTick(); context.takeScreenshot("appearance-settings-ko");
        context.clickScreenButton("feedback_flag.save"); context.waitForScreen(DashboardScreen.class);
        var saved = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().visuals());
        check(saved.outlineWidth() > 2 && saved.flagScale() > 1 && saved.offsetX() > 0 && saved.offsetY() > 0 && saved.offsetZ() < 0,
                "Appearance sliders save width, size and all three position axes");
        check(!saved.showNames() && !saved.throughWalls(), "Name and occluded outline switches save independently");
        context.runOnClient(mc -> {
            var book = FeedbackFlagClient.manager.book();
            var target = book.flags().getFirst().target();
            check(target.x() == 0 && target.y() == 81 && target.z() == 0, "Visual movement never changes observed block coordinates");
            var marker = FeedbackFlagClient.extractMarkers().getFirst(); var old = FlagGeometry.of(target, VisualSettings.DEFAULT);
            check(Math.abs((marker.cloth().maxX - marker.cloth().minX) / (old.cloth().maxX - old.cloth().minX) - saved.flagScale()) < .000001,
                    "Rendered geometry uses selected size");
            check(marker.label().isEmpty() && !marker.throughWalls() && marker.outlineWidth() >= saved.outlineWidth(), "Renderer uses width and visibility preferences");
        });
        context.setScreen(() -> null);
        var center = context.computeOnClient(mc -> FlagGeometry.of(FeedbackFlagClient.manager.book().flags().getFirst().target(), saved).cloth().getCenter());
        aim(context, center.x, center.y, center.z); context.waitTicks(5); context.takeScreenshot("custom-appearance-world-ko");
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.hitFlag() != null && FeedbackFlagClient.manager.hitFlag().id()
                .equals(FeedbackFlagClient.manager.book().flags().getFirst().id()), "Resized relocated flag remains selectable at its drawn position"));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitForScreen(FlagEditScreen.class);
        context.clickScreenButton("gui.cancel"); context.waitForScreen(DashboardScreen.class);
        context.clickScreenButton("feedback_flag.visual_settings"); context.waitForScreen(VisualSettingsScreen.class);
        context.clickScreenButton("feedback_flag.visual.defaults"); context.clickScreenButton("gui.cancel");
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().visuals().equals(saved), "Cancel discards restoring defaults"));
        context.clickScreenButton("feedback_flag.visual_settings"); context.waitForScreen(VisualSettingsScreen.class);
        context.clickScreenButton("feedback_flag.visual.defaults");
        context.getInput().resizeWindow(854, 480); context.waitTick(); context.takeScreenshot("appearance-defaults-small-ko");
        context.clickScreenButton("feedback_flag.save");
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().visuals().equals(VisualSettings.DEFAULT)
                && FeedbackFlagClient.manager.book().displayMode() == FlagBook.DisplayMode.FLAGS
                && FeedbackFlagClient.manager.book().transparency() == 15, "Save restores all appearance defaults"));
    }
    private static void assignKey(ClientGameTestContext context, int index, int key) {
        context.runOnClient(mc -> {
            var screen = (KeySettingsScreen)mc.gui.screen();
            ((net.minecraft.client.gui.components.Button)screen.children().get(index * 2))
                    .onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0));
        });
    }
    private static void writeMemo(ClientGameTestContext context) {
        context.waitForScreen(FlagEditScreen.class);
        context.runOnClient(mc -> mc.gui.screen().setFocused(memoEditor(mc)));
        context.getInput().typeChars("회로 입력"); context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.getInput().typeChars("리셋 전 확인");
        context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Inline multiline keyboard input"));
        context.getInput().resizeWindow(854, 480); context.waitTick();
        context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Inline memo draft survives resizing"));
        context.takeScreenshot("memo-inline-input");
        context.runOnClient(mc -> {
            check(FeedbackFlagClient.manager.book().flags().getFirst().memo().isEmpty(), "Inline editing remains a draft until Save");
            memoEditor(mc).setValue("가".repeat(FlagBook.MAX_MEMO + 1));
            check(!button(mc, "feedback_flag.save").active, "Overlong inline memo cannot be saved");
            memoEditor(mc).setValue(MEMO);
            check(button(mc, "feedback_flag.save").active, "Valid memo can be saved");
            var swatches = mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> b.getMessage().getString().equals("■")).toList();
            check(swatches.size() == 16, "Sixteen basic color choices are visible");
            check(BasicPalette.COLORS.stream().noneMatch(c -> c.label().getString().toLowerCase().contains("concrete")
                    || c.label().getString().contains("콘크리트")), "Palette labels contain only color names");
            swatches.getLast().onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            var boxes = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            check(boxes.get(1).getValue().equals("080A0F"), "Black swatch selects the unchanged color");
            boxes.get(2).setValue("1,2,3,4,5,6,7,8,9");
            check(!button(mc, "feedback_flag.save").active, "Too many tags disable Save");
            boxes.get(2).setValue("입력, 공통");
            check(button(mc, "feedback_flag.save").active, "Valid tags enable Save");
        });
        context.getInput().resizeWindow(1000, 700); context.waitTick();
    }
    private static void searchFields(ClientGameTestContext context, String name, String color, String tags) {
        context.runOnClient(mc -> {
            var boxes = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            boxes.get(0).setValue(name); boxes.get(1).setValue(color); boxes.get(2).setValue(tags);
        });
    }
    private static long searchResults(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(b -> b.visible && b.getWidth() > 24 && b.getY() >= 177 && b.getY() < mc.gui.screen().height - 29).count());
    }
    private static void testSearch(ClientGameTestContext context) {
        context.clickScreenButton("feedback_flag.search"); context.waitForScreen(FlagSearchScreen.class);
        searchFields(context, "signal", "#56c8e8", "입력, 공통");
        context.clickScreenButton("feedback_flag.search_active.all");
        check(searchResults(context) == 1 && visibleLabel(context, "Signal A").contains("활성"), "Four search fields intersect in the real UI");
        context.waitTick(); context.takeScreenshot("flag-search-ko");
        context.getInput().resizeWindow(1000, 700); context.waitTick();
        check(searchResults(context) == 1, "Search draft survives resize");
        context.clickScreenButton(visibleLabel(context, "Signal A")); context.waitForScreen(FlagEditScreen.class);
        context.clickScreenButton("gui.cancel"); context.waitForScreen(FlagSearchScreen.class);
        check(searchResults(context) == 1, "Returning from editor keeps search criteria");
        context.clickScreenButton("feedback_flag.search_active.enabled");
        check(searchResults(context) == 0, "Inactive filter excludes enabled flag");
        context.waitTick(); context.takeScreenshot("flag-search-empty-ko");
        context.clickScreenButton("feedback_flag.search_reset");
        context.runOnClient(mc -> {
            var book = FeedbackFlagClient.manager.book(); var flag = book.flags().getFirst();
            book.edit(flag.id(), flag.text(), flag.color(), false, true, true);
        });
        context.clickScreenButton("feedback_flag.search_active.all");
        context.clickScreenButton("feedback_flag.search_active.enabled");
        check(searchResults(context) == 1 && visibleLabel(context, "Signal A").contains("비활성"), "Disabled flag is found after data change");
        context.clickScreenButton("feedback_flag.search_reset");
        context.runOnClient(mc -> {
            var book = FeedbackFlagClient.manager.book(); var flag = book.flags().getFirst();
            book.edit(flag.id(), flag.text(), flag.color(), true, true, true);
        });
        searchFields(context, "", "없는색", ""); check(searchResults(context) == 0, "Unknown color gives empty results");
        searchFields(context, "", "56C8E8", ""); check(searchResults(context) == 1, "Custom RGB color without hash finds flag");
        context.clickScreenButton("feedback_flag.search_reset");
        context.clickScreenButton("feedback_flag.back"); context.waitForScreen(DashboardScreen.class);
        var deleted = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().flags().getFirst());
        context.setScreen(() -> new FlagEditScreen(new DashboardScreen(FeedbackFlagClient.manager, null), FeedbackFlagClient.manager, deleted.id()));
        context.clickScreenButton("feedback_flag.delete"); context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.clickScreenButton("gui.no"); context.waitForScreen(FlagEditScreen.class);
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flag(deleted.id()).equals(deleted), "Cancel single deletion preserves metadata"));
        context.clickScreenButton("feedback_flag.delete"); context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
        context.clickScreenButton("gui.yes"); context.waitForScreen(DashboardScreen.class);
        context.clickScreenButton("feedback_flag.undo"); context.waitTick();
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flag(deleted.id()).equals(deleted)
                && !button(mc, "feedback_flag.undo").active, "Single deletion Undo restores saved metadata once"));
        context.takeScreenshot("flag-undo-ko");
        context.getInput().resizeWindow(854, 480); context.waitTick();
    }
    private static void prepareBulk(ClientGameTestContext context) {
        context.clickScreenButton(visibleLabel(context, "색상:"));
        context.clickScreenButton(visibleLabel(context, "활성:")); context.clickScreenButton(visibleLabel(context, "활성:"));
        context.clickScreenButton(visibleLabel(context, "태그:"));
        context.runOnClient(mc -> {
            var boxes = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            boxes.get(0).setValue("F1AF15"); boxes.get(1).setValue("일괄 태그");
        });
    }
    private static boolean hasButton(net.minecraft.client.Minecraft mc, String key) {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        return mc.gui.screen() != null && mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).anyMatch(b -> b.visible && b.active && b.getMessage().getString().equals(label));
    }
    private static void saveArchive(ClientGameTestContext context, String name) {
        context.clickScreenButton("feedback_flag.archive.save"); context.waitForScreen(ArchiveSaveScreen.class);
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(name));
        context.clickScreenButton("feedback_flag.archive.save");
        context.waitFor(mc -> hasButton(mc, "feedback_flag.archive.view"));
    }
    private static void testBulkAndArchives(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            var manager = FeedbackFlagClient.manager; var book = manager.book();
            for (int i = 0; i < 2; i++) {
                var p = new net.minecraft.core.BlockPos(3 + i, 80, 4);
                var f = book.add(manager.target(p), FlagManager.snapshot(mc.level.getBlockState(p)), "Batch " + (i == 0 ? "A" : "B"));
                book.edit(f.id(), f.text(), "보존할 메모 " + i, java.util.List.of(i == 0 ? "입력" : "출력"), f.color(), true, true, true);
            }
        });
        context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
        context.clickScreenButton("feedback_flag.search"); context.waitForScreen(FlagSearchScreen.class);
        searchFields(context, "Batch", "", ""); context.clickScreenButton("feedback_flag.bulk.select_results");
        check(visibleLabel(context, "선택 편집").contains("2"), "Select results includes flags on other pages");
        context.takeScreenshot("bulk-selection-small-ko");
        var before = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().data());
        context.clickScreenButton(visibleLabel(context, "선택 편집")); context.waitForScreen(BulkEditScreen.class); prepareBulk(context);
        context.waitTick(); context.takeScreenshot("bulk-edit-small-ko");
        context.clickScreenButton("gui.cancel"); context.waitForScreen(FlagSearchScreen.class);
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().data().equals(before), "Cancelling bulk edit changes nothing"));
        context.clickScreenButton(visibleLabel(context, "선택 편집")); context.waitForScreen(BulkEditScreen.class); prepareBulk(context);
        context.clickScreenButton(visibleLabel(context, "2개에 적용")); context.waitForScreen(FlagSearchScreen.class);
        context.runOnClient(mc -> {
            var book = FeedbackFlagClient.manager.book();
            check(book.flags().stream().filter(f -> f.text().startsWith("Batch")).allMatch(f -> f.color() == 0xF1AF15 && !f.enabled()
                    && f.tags().size() == 2 && f.tags().contains("일괄 태그") && f.memo().startsWith("보존할 메모")), "Bulk Apply changes only selected fields and preserves memos");
            check(book.flags().stream().filter(f -> !f.text().startsWith("Batch")).allMatch(f -> f.enabled() && f.color() == 0x2489C7), "Bulk edit leaves unselected flag alone");
            var a = book.flags().stream().filter(f -> f.text().equals("Batch A")).findFirst().orElseThrow();
            book.edit(a.id(), a.text(), a.color(), true, true, true); book.clearHistory(null);
            var stone = new FlagBook.Snapshot("minecraft:stone", java.util.Map.of()); var air = new FlagBook.Snapshot("minecraft:air", java.util.Map.of());
            book.seed(a.target(), stone); book.observe(a.target(), air, 10, 1000); book.observe(a.target(), stone, 11, 1020); book.observe(a.target(), air, 12, 1040);
        });
        context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
        context.runOnClient(mc -> {
            var explore = button(mc, "feedback_flag.timeline.open");
            var archives = button(mc, "feedback_flag.archive.library");
            var clear = button(mc, "feedback_flag.clear_history");
            check(explore.getY() == clear.getY() && archives.getY() == clear.getY()
                    && explore.getRight() < archives.getX() && archives.getRight() < clear.getX(), "Timeline actions sit left of reset without overlap");
        });
        context.waitTick(); context.takeScreenshot("timeline-actions-ko");
        context.clickScreenButton("feedback_flag.timeline.open"); context.waitForScreen(TimelineScreen.class);
        context.waitTick(); context.takeScreenshot("timeline-explorer-small-ko");
        context.clickScreenButton("+");
        check(visibleLabel(context, "▶ ").contains("20틱"), "Zoom shows the middle interval at its original relative tick");
        adjustSlider(context, "구간 위치:", 1000);
        check(visibleLabel(context, "▶ ").contains("40틱"), "Position slider reaches latest interval");
        adjustSlider(context, "구간 위치:", -1000);
        context.runOnClient(mc -> check(mc.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.AbstractSliderButton.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractSliderButton.class::cast)
                .anyMatch(b -> b.getMessage().getString().equals("구간 위치: 0%")), "Position slider reaches first interval"));
        context.clickScreenButton("feedback_flag.timeline.fit"); context.clickScreenButton("+");
        context.waitTick(); context.takeScreenshot("timeline-zoom-ko");
        saveArchive(context, "Zoomed 20 ticks"); context.takeScreenshot("archive-saved-small-ko");
        context.clickScreenButton("feedback_flag.archive.view"); context.waitForScreen(TimelineScreen.class);
        check(visibleLabel(context, "▶ ").contains("20틱"), "Saved interval preserves first-ever zero instead of rebasing");
        context.clickScreenButton(visibleLabel(context, "▶ ")); context.clickScreenButton(visibleLabel(context, "↳ ")); context.waitForScreen(EventScreen.class);
        context.runOnClient(mc -> check(!hasButton(mc, "feedback_flag.edit_title"), "Saved detail cannot edit the live flag"));
        context.takeScreenshot("archive-event-detail-ko");
        context.clickScreenButton("feedback_flag.back"); context.clickScreenButton("feedback_flag.back"); context.waitForScreen(ArchiveSaveScreen.class);
        context.clickScreenButton("feedback_flag.back"); context.waitForScreen(TimelineScreen.class);
        context.clickScreenButton("feedback_flag.timeline.fit"); saveArchive(context, "Full circuit run");
        context.clickScreenButton("feedback_flag.archive.view"); context.waitForScreen(TimelineScreen.class);
        context.runOnClient(mc -> FeedbackFlagClient.manager.book().clearHistory(null));
        check(visibleLabel(context, "▶ ").contains("40틱"), "Archive remains intact after live timeline reset");
        context.clickScreenButton("feedback_flag.archive.library"); context.waitForScreen(ArchiveLibraryScreen.class);
        context.waitFor(mc -> mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).anyMatch(b -> b.visible && b.getMessage().getString().startsWith("Full circuit run")));
        context.waitTick(); context.takeScreenshot("archive-library-ko");
        context.clickScreenButton(visibleLabel(context, "Full circuit run")); context.waitForScreen(TimelineScreen.class);
        check(visibleLabel(context, "▶ ").contains("40틱"), "Library reopens the saved file");
        context.runOnClient(mc -> {
            try {
                var files = ArchiveLibraryScreen.files(); var entry = files.list().stream().filter(e -> e.name().equals("Full circuit run")).findFirst().orElseThrow();
                var archive = files.read(entry.folder()); check(archive.data().events().size() == 3, "Fit-all save includes every page");
                var preview = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("../../html-preview").normalize();
                java.nio.file.Files.createDirectories(preview);
                java.nio.file.Files.copy(entry.folder().resolve("timeline.html"), preview.resolve("timeline.html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                check(files.list().stream().anyMatch(e -> e.name().equals("Zoomed 20 ticks") && e.events() == 1), "Zoomed save contains only that interval");
            } catch (java.io.IOException ex) { throw new AssertionError(ex); }
        });
    }
    private static void testModMenu(ClientGameTestContext context) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) return;
        context.getInput().resizeWindow(1000, 700);
        context.runOnClient(mc -> {
            try {
                Class<?> type = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) type.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance((Object)null));
                mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Feedback Flag");
                var list = mc.gui.screen().children().stream().filter(w -> w.getClass().getSimpleName().equals("ModListWidget")).findFirst().orElseThrow();
                var entry = list.getClass().getMethod("getEntry", int.class).invoke(list, 0);
                var entryType = Class.forName("com.terraformersmc.modmenu.gui.widget.entries.ModListEntry");
                list.getClass().getMethod("select", entryType).invoke(list, entry);
                Object mod = entryType.getMethod("getMod").invoke(entry);
                var modType = Class.forName("com.terraformersmc.modmenu.util.mod.Mod");
                check(modType.getMethod("getId").invoke(mod).equals("feedback_flag"), "Mod Menu finds Feedback Flag");
                String description = (String) modType.getMethod("getTranslatedDescription").invoke(mod);
                check(description.contains("개인용 플래그") && description.contains("/feedbackflag"), "Mod Menu reads localized Korean description");
                check(!entryType.getMethod("getIconTexture").invoke(entry).equals(entryType.getField("UNKNOWN_ICON").get(null)), "Mod Menu loads the custom icon");
            } catch (ReflectiveOperationException ex) { throw new AssertionError("Mod Menu integration", ex); }
        });
        context.waitTicks(3); context.takeScreenshot("mod-menu-ko");
    }
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1000, 700);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("fill -4 80 -4 4 80 5 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=false]");
            world.getServer().runCommand("tp @a 0.5 81 3.5 180 0");
            world.getConnection().waitForClientboundPackets();
            context.waitFor(mc -> FeedbackFlagClient.manager.book() != null);
            context.runOnClient(mc -> check(FeedbackFlagClient.openMenu.isUnbound() && FeedbackFlagClient.placeFlag.isUnbound(), "Both shortcuts must start unbound"));
            aim(context, .5, 81.18, .5);
            command(context, "feedbackflag add");
            writeMemo(context);
            edit(context, "Signal A", "56C8E8");
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flags().getFirst().memo().equals(MEMO), "Flag Save commits the memo"));
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=true]");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().size() == 1);
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=false]");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().size() == 2);
            context.runOnClient(mc -> {
                var event = FeedbackFlagClient.manager.book().events().getFirst();
                check(event.kind() == FlagBook.Kind.STATE, "Lever should be a state event");
                check(FeedbackFlagClient.manager.book().relativeTick(event) == 0, "First real server change displays zero ticks");
                check("false".equals(event.before().properties().get("powered")), "Old lever state");
                check("true".equals(event.after().properties().get("powered")), "New lever state");
                check(FeedbackFlagClient.manager.book().flags().getFirst().memo().equals(MEMO), "Server updates preserve memos");
            });
            context.setScreen(() -> null);
            aim(context, 2.5, 81, .5);
            command(context, "feedbackflag add"); edit(context, "Block B", "EF758C");
            world.getServer().runCommand("setblock 2 80 0 minecraft:gold_block");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().size() == 3);
            context.takeScreenshot("dashboard-en");
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().events().getLast().kind() == FlagBook.Kind.BLOCK, "Replacement event"));
            context.clickScreenButton(visibleLabel(context, "▶ "));
            context.clickScreenButton(visibleLabel(context, "↳ "));
            context.waitForScreen(EventScreen.class);
            context.waitTick(); context.takeScreenshot("event-detail-en");
            context.setScreen(() -> null);
            aim(context, .5, 81.18, .5);
            // Ordinary lever right-click must still work; the flag itself lives above the lever.
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().size() >= 4);
            context.runOnClient(mc -> check(mc.gui.screen() == null, "Normal lever click must not open settings"));
            // Direct right-click on the flag cloth must open its editor without sending block use.
            aim(context, .75, 82.65, .5);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(FlagEditScreen.class);
            context.takeScreenshot("flag-settings-en");
            context.clickScreenButton("gui.cancel");
            context.clickScreenButton("feedback_flag.keys");
            context.waitForScreen(KeySettingsScreen.class);
            context.takeScreenshot("shortcut-unbound-en");
            assignKey(context, 0, GLFW.GLFW_KEY_F8);
            assignKey(context, 1, GLFW.GLFW_KEY_F9);
            context.runOnClient(mc -> check(!FeedbackFlagClient.openMenu.isUnbound() && !FeedbackFlagClient.placeFlag.isUnbound(), "Both shortcuts assigned independently"));
            assignKey(context, 1, GLFW.GLFW_KEY_ESCAPE);
            context.runOnClient(mc -> check(FeedbackFlagClient.placeFlag.isUnbound() && !FeedbackFlagClient.openMenu.isUnbound(), "Escape unbinds only placement"));
            assignKey(context, 1, GLFW.GLFW_KEY_F9);
            context.setScreen(() -> null);
            context.getInput().pressKey(GLFW.GLFW_KEY_F8);
            context.waitForScreen(DashboardScreen.class);
            context.clickScreenButton("feedback_flag.keys"); context.clickScreenButton("feedback_flag.unbind");
            context.runOnClient(mc -> check(FeedbackFlagClient.openMenu.isUnbound() && !FeedbackFlagClient.placeFlag.isUnbound(), "Menu clearing preserves placement shortcut"));
            context.clickScreenButton("feedback_flag.back");
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTick();
            context.runOnClient(mc -> check(mc.gui.screen() instanceof DashboardScreen && !FeedbackFlagClient.manager.placing(), "Placement shortcut is inactive inside screens"));
            context.setScreen(() -> null);
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTick();
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.placing(), "Placement shortcut starts placing"));
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTick();
            context.runOnClient(mc -> check(!FeedbackFlagClient.manager.placing(), "Placement shortcut cancels placing"));
            context.runOnClient(mc -> FeedbackFlagClient.manager.book().setEnabled(false));
            int count = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().events().size());
            world.getServer().runCommand("setblock 0 81 0 minecraft:air");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(5);
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().events().size() == count, "Paused recording"));
            context.setScreen(() -> null);
            aim(context, -1.5, 81, .5);
            command(context, "ff"); context.waitForScreen(DashboardScreen.class);
            context.clickScreenButton("feedback_flag.place");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            edit(context, "Placed C", "6EDD9B");
            context.runOnClient(mc -> {
                check(FeedbackFlagClient.manager.book().flags().size() == 3, "Menu placement mode");
                check(!FeedbackFlagClient.manager.placing(), "Placement ends after one flag");
            });
            context.setScreen(() -> null);
            aim(context, 1.5, 81, .5);
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            edit(context, "Shortcut D", "F2D16B");
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flags().size() == 4 && !FeedbackFlagClient.manager.placing(), "Shortcut places one flag and exits placement"));
            context.runOnClient(mc -> {
                FeedbackFlagClient.manager.book().setEnabled(true);
                check(FeedbackFlagClient.manager.save(), "Persistent save");
                mc.getLanguageManager().setSelected("ko_kr"); mc.options.languageCode = "ko_kr";
                mc.reloadResourcePacks();
            });
            context.waitFor(mc -> mc.gui.overlay() == null);
            context.getInput().resizeWindow(854, 480);
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
            context.waitTicks(5); context.takeScreenshot("dashboard-ko");
            context.runOnClient(mc -> mc.gui.setScreen(new FlagEditScreen(new DashboardScreen(FeedbackFlagClient.manager, null),
                    FeedbackFlagClient.manager, FeedbackFlagClient.manager.book().flags().getFirst().id())));
            context.waitTick(); context.takeScreenshot("flag-settings-ko");
            context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Saved memo reopens in Korean UI"));
            context.takeScreenshot("memo-ko");
            context.runOnClient(mc -> memoEditor(mc).setValue("취소할 변경"));
            context.clickScreenButton("gui.cancel");
            context.runOnClient(mc -> {
                check(FeedbackFlagClient.manager.book().flags().getFirst().memo().equals(MEMO), "Cancelling flag settings discards inline memo draft");
                var manager = FeedbackFlagClient.manager;
                mc.gui.setScreen(new EventScreen(new DashboardScreen(manager, null), manager, manager.book().events().getFirst()));
            });
            context.waitTick(); context.takeScreenshot("memo-event-detail-ko");
            for (int i = 0; i < 4; i++) { context.getInput().scroll(-1); context.waitTick(); }
            context.takeScreenshot("memo-event-detail-scrolled-ko");
            context.setScreen(() -> new KeySettingsScreen(new DashboardScreen(FeedbackFlagClient.manager, null)));
            context.waitTick(); context.takeScreenshot("shortcuts-ko");
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
            world.getServer().runCommand("tick freeze");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
            long frozenTick = context.computeOnClient(mc -> mc.level.getGameTime());
            world.getServer().runCommand("setblock 2 80 0 minecraft:diamond_block");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().getLast().after().block().equals("minecraft:diamond_block"));
            context.runOnClient(mc -> {
                var event = FeedbackFlagClient.manager.book().events().getLast();
                check(event.worldTick() && event.tick() == frozenTick, "Received event uses the frozen world tick");
            });
            context.waitTicks(5);
            world.getServer().runCommand("setblock 2 80 0 minecraft:emerald_block");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().getLast().after().block().equals("minecraft:emerald_block"));
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().events().getLast().tick() == frozenTick, "Client ticks do not advance a frozen world clock"));
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=true]");
            world.getServer().runCommand("setblock -2 80 0 minecraft:redstone_block");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book(); var group = TickGroup.collect(book, null, null).getFirst();
                check(group.representative().tick() == frozenTick && group.flags().size() == 3 && group.events().size() == 4, "Three flags and four changes form one actual game-tick group");
                check(book.relativeTick(group.representative()) == frozenTick - book.data().tickOrigin(), "Group uses shared relative origin");
            });
            context.getInput().resizeWindow(1000, 700);
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
            context.waitTick(); context.takeScreenshot("tick-groups-collapsed-ko");
            String collapsed = visibleLabel(context, "▶ ");
            check(collapsed.contains("외 2개") && !collapsed.contains("Signal A") && !collapsed.contains("Block B"), "Collapsed group shows count without flag names");
            context.clickScreenButton(collapsed); context.waitTick(); context.takeScreenshot("tick-groups-expanded-ko");
            context.clickScreenButton(visibleLabel(context, "↳ ")); context.waitForScreen(EventScreen.class);
            context.clickScreenButton("feedback_flag.back"); context.waitForScreen(DashboardScreen.class);
            context.clickScreenButton(visibleLabel(context, "▼ ")); context.waitTick();
            openTagFilter(context); context.takeScreenshot("tag-filter-ko");
            context.clickScreenButton("입력"); context.waitForScreen(DashboardScreen.class);
            context.waitTick(); context.takeScreenshot("tag-input-timeline-ko");
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book(); var group = TickGroup.collect(book, null, "입력").getFirst();
                check(group.flags().size() == 1 && book.relativeTick(group.representative()) != 0, "Tag filter retains global relative tick");
            });
            context.clickScreenButton(visibleLabel(context, "▶ "));
            check(visibleLabel(context, "↳ ").contains("Signal A"), "Input tag expands only its matching flag");
            openTagFilter(context); context.clickScreenButton("feedback_flag.untagged"); context.waitForScreen(DashboardScreen.class);
            context.runOnClient(mc -> check(mc.gui.screen().children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast)
                    .noneMatch(b -> b.visible && (b.getMessage().getString().startsWith("▶ ") || b.getMessage().getString().startsWith("↳ "))), "Untagged filter hides unrelated records"));
            openTagFilter(context); context.clickScreenButton("feedback_flag.all_tags"); context.waitForScreen(DashboardScreen.class);
            context.getInput().resizeWindow(854, 480); context.waitTick();
            context.clickScreenButton(visibleLabel(context, "▶ "));
            context.getInput().scroll(-2); context.waitTick(); context.takeScreenshot("expanded-small-window-ko");
            check(visibleLabel(context, "↳ ").startsWith("↳ "), "Expanded children remain reachable in a small window");
            world.getServer().runCommand("tick unfreeze");
            var firstId = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().flags().getFirst().id());
            int beforeReset = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().events().size());
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
            context.clickScreenButton("feedback_flag.clear_history");
            context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.takeScreenshot("reset-confirmation-ko");
            context.clickScreenButton("gui.no"); context.waitForScreen(DashboardScreen.class);
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().events().size() == beforeReset, "Cancelling reset preserves records"));
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, firstId));
            context.clickScreenButton("feedback_flag.clear_flag_history");
            context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.clickScreenButton("gui.yes"); context.waitForScreen(DashboardScreen.class);
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book();
                check(!book.events().isEmpty() && book.events().stream().noneMatch(e -> e.flagId().equals(firstId)), "Filtered reset keeps other flags' records");
                check(book.flag(firstId).total() == 0 && book.flag(firstId).memo().equals(MEMO), "Filtered reset keeps its flag and memo");
            });
            context.clickScreenButton("feedback_flag.all_flags");
            context.clickScreenButton("feedback_flag.clear_history");
            context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.clickScreenButton("gui.yes"); context.waitForScreen(DashboardScreen.class);
            context.waitTick(); context.takeScreenshot("timeline-reset-ko");
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book();
                check(book.events().isEmpty() && book.flags().size() == 4 && book.flags().stream().allMatch(f -> f.total() == 0), "Global reset clears only history and counters");
                check(!button(mc, "feedback_flag.clear_history").active, "Empty timeline reset button disabled");
                check(FeedbackFlagClient.manager.save(), "Reset persists successfully");
            });
            world.getServer().runCommand("setblock 2 80 0 minecraft:stone");
            context.waitFor(mc -> FeedbackFlagClient.manager.book().events().size() == 1);
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flags().get(1).total() == 1 && FeedbackFlagClient.manager.book().relativeTick(FeedbackFlagClient.manager.book().events().getFirst()) == 0, "Recording resumes from zero after reset"));
            for (var mode : new FlagBook.DisplayMode[]{FlagBook.DisplayMode.BLOCKS, FlagBook.DisplayMode.BOTH, FlagBook.DisplayMode.FLAGS}) {
                context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
                context.clickScreenButton(visibleLabel(context, "표시:"));
                context.setScreen(() -> null); aim(context, .75, 82.1, .5);
                context.runOnClient(mc -> {
                    check(FeedbackFlagClient.manager.book().displayMode() == mode, "Display button cycles mode");
                    var markers = FeedbackFlagClient.extractMarkers();
                    check(!markers.isEmpty() && markers.stream().allMatch(m -> m.drawFlag() == mode.flags() && m.drawBlock() == mode.blocks()), "Renderer honors both independent display choices");
                    if (mode == FlagBook.DisplayMode.BLOCKS) check(FeedbackFlagClient.manager.hitFlag() == null, "Hidden flag geometry cannot intercept clicks");
                });
                world.getConnection().waitForChunksRender(); context.waitTicks(5); context.takeScreenshot("display-" + mode.name().toLowerCase() + "-ko");
            }
            context.runOnClient(mc -> {
                FeedbackFlagClient.manager.book().setTransparency(100);
                check(FeedbackFlagClient.extractMarkers().isEmpty(), "Global transparency also hides outlines");
                FeedbackFlagClient.manager.book().setTransparency(15);
            });
            context.setScreen(() -> null);
            aim(context, .75, 82.65, .5);
            world.getConnection().waitForChunksRender();
            context.waitTicks(5); context.takeScreenshot("world-flags");
            testAppearance(context);
            testSearch(context);
            var beforeRemoval = context.computeOnClient(mc -> FeedbackFlagClient.manager.book().data());
            var observedBlock = context.computeOnClient(mc -> FlagManager.snapshot(mc.level.getBlockState(new net.minecraft.core.BlockPos(2, 80, 0))));
            context.runOnClient(mc -> FeedbackFlagClient.manager.startPlacing());
            context.setScreen(() -> new DashboardScreen(FeedbackFlagClient.manager, null));
            context.clickScreenButton("feedback_flag.remove_all"); context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.waitTick(); context.takeScreenshot("remove-all-confirmation-ko");
            context.clickScreenButton("gui.no"); context.waitForScreen(DashboardScreen.class);
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().data().equals(beforeRemoval), "Cancel all-removal preserves every flag and history"));
            context.clickScreenButton("feedback_flag.remove_all"); context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.clickScreenButton("gui.yes"); context.waitForScreen(DashboardScreen.class); context.waitTick();
            context.takeScreenshot("all-flags-removed-ko");
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book();
                check(book.flags().isEmpty() && book.events().size() == beforeRemoval.events().size(), "Confirmed all-removal preserves timeline records");
                check(!FeedbackFlagClient.manager.placing() && !button(mc, "feedback_flag.remove_all").active, "All-removal cancels placement and disables the empty action");
                check(FeedbackFlagClient.extractMarkers().isEmpty(), "Removed flags leave no world render markers");
                check(FlagManager.snapshot(mc.level.getBlockState(new net.minecraft.core.BlockPos(2, 80, 0))).equals(observedBlock), "All-removal never modifies actual blocks");
                check(FeedbackFlagClient.manager.save(), "All-removal persists");
            });
            context.clickScreenButton("feedback_flag.undo"); context.waitTick();
            context.runOnClient(mc -> {
                var book = FeedbackFlagClient.manager.book();
                check(book.flags().equals(beforeRemoval.flags()) && book.events().size() == beforeRemoval.events().size(), "Bulk undo restores every flag without duplicating history");
                check(!button(mc, "feedback_flag.undo").active && !FeedbackFlagClient.extractMarkers().isEmpty(), "Bulk undo consumes the snapshot and restores markers");
            });
            context.takeScreenshot("all-flags-restored-ko");
            context.clickScreenButton("feedback_flag.remove_all"); context.waitForScreen(net.minecraft.client.gui.screens.ConfirmScreen.class);
            context.clickScreenButton("gui.yes"); context.waitForScreen(DashboardScreen.class);
            context.setScreen(() -> null); aim(context, 2.5, 81, .5);
            command(context, "feedbackflag add"); edit(context, "After removal", "2489C7");
            context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flags().size() == 1, "Flags can be added normally after all-removal"));
            context.runOnClient(mc -> check(!button(mc, "feedback_flag.undo").active
                    && FeedbackFlagClient.manager.book().undoStatus() == FlagBook.UndoStatus.CONFLICT, "New flag at a restore position disables Undo"));
            context.clickScreenButton("feedback_flag.search"); context.waitForScreen(FlagSearchScreen.class);
            searchFields(context, "", "하늘", ""); check(searchResults(context) == 1, "Localized basic color searches live flag");
            context.runOnClient(mc -> {
                var swatch = mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                        .map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> b.getMessage().getString().equals("■")).toList().get(3);
                swatch.onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            });
            check(searchResults(context) == 1, "Color swatch selects an exact RGB search");
            context.takeScreenshot("flag-search-color-ko");
            testBulkAndArchives(context);
            testModMenu(context);
        }
    }
}
