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
    private static void writeMemo(ClientGameTestContext context) {
        context.clickScreenButton("feedback_flag.memo_edit"); context.waitForScreen(MemoScreen.class);
        context.getInput().typeChars("회로 입력"); context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.getInput().typeChars("리셋 전 확인");
        context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Multiline keyboard input"));
        context.getInput().resizeWindow(854, 480); context.waitTick();
        context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Memo draft survives resizing"));
        context.takeScreenshot("memo-input");
        context.clickScreenButton("feedback_flag.memo_apply"); context.waitForScreen(FlagEditScreen.class);
        context.runOnClient(mc -> check(FeedbackFlagClient.manager.book().flags().getFirst().memo().isEmpty(), "Apply updates only the draft"));
        context.clickScreenButton("feedback_flag.memo_edit"); context.waitForScreen(MemoScreen.class);
        context.runOnClient(mc -> {
            check(memoEditor(mc).getValue().equals(MEMO), "Applied draft reopens intact");
            memoEditor(mc).setValue("가".repeat(FlagBook.MAX_MEMO + 1));
            var apply = mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).findFirst().orElseThrow();
            check(!apply.active, "Overlong memo cannot be applied");
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitForScreen(FlagEditScreen.class);
        context.clickScreenButton("feedback_flag.memo_edit"); context.waitForScreen(MemoScreen.class);
        context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Escape discards only the subeditor changes"));
        context.clickScreenButton("gui.cancel"); context.waitForScreen(FlagEditScreen.class);
        context.getInput().resizeWindow(1000, 700); context.waitTick();
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
            context.runOnClient(mc -> check(FeedbackFlagClient.openMenu.isUnbound(), "Shortcut must start unbound"));
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
            String eventButton = context.computeOnClient(mc -> mc.gui.screen().children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(b -> b.visible && b.getMessage().getString().matches("[0-9]{2}:.*"))
                    .findFirst().orElseThrow().getMessage().getString());
            context.clickScreenButton(eventButton);
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
            context.runOnClient(mc -> {
                var screen = (KeySettingsScreen)mc.gui.screen();
                ((net.minecraft.client.gui.components.Button)screen.children().getFirst()).onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_F8, 0, 0));
                check(!FeedbackFlagClient.openMenu.isUnbound(), "Shortcut assignment");
            });
            context.setScreen(() -> null);
            context.getInput().pressKey(GLFW.GLFW_KEY_F8);
            context.waitForScreen(DashboardScreen.class);
            context.clickScreenButton("feedback_flag.keys"); context.clickScreenButton("feedback_flag.unbind");
            context.runOnClient(mc -> check(FeedbackFlagClient.openMenu.isUnbound(), "Shortcut clearing"));
            context.clickScreenButton("feedback_flag.back");
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
            context.clickScreenButton("feedback_flag.memo_edit"); context.waitForScreen(MemoScreen.class);
            context.runOnClient(mc -> check(memoEditor(mc).getValue().equals(MEMO), "Saved memo reopens in Korean UI"));
            context.takeScreenshot("memo-ko");
            context.runOnClient(mc -> memoEditor(mc).setValue("취소할 변경"));
            context.clickScreenButton("feedback_flag.memo_apply"); context.waitForScreen(FlagEditScreen.class);
            context.clickScreenButton("gui.cancel");
            context.runOnClient(mc -> {
                check(FeedbackFlagClient.manager.book().flags().getFirst().memo().equals(MEMO), "Cancelling flag settings discards applied memo draft");
                var manager = FeedbackFlagClient.manager;
                mc.gui.setScreen(new EventScreen(new DashboardScreen(manager, null), manager, manager.book().events().getFirst()));
            });
            context.waitTick(); context.takeScreenshot("memo-event-detail-ko");
            for (int i = 0; i < 4; i++) { context.getInput().scroll(-1); context.waitTick(); }
            context.takeScreenshot("memo-event-detail-scrolled-ko");
            context.setScreen(() -> null);
            aim(context, .75, 82.65, .5);
            world.getConnection().waitForChunksRender();
            context.waitTicks(5); context.takeScreenshot("world-flags");
        }
    }
}
