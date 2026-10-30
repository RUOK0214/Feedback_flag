package io.github.ruok0214.feedbackflag;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class KeySettingsScreen extends FlagScreen {
    private int waiting = -1;
    private final KeyMapping[] mappings = {FeedbackFlagClient.openMenu, FeedbackFlagClient.placeFlag};
    private final String[] labels = {"binding", "binding_place"};
    private final Button[] bindings = new Button[2];
    KeySettingsScreen(Screen parent) { super("keys", parent); }
    @Override protected void init() {
        int w = Math.min(420, width - 30), x = (width - w) / 2;
        for (int i = 0; i < mappings.length; i++) {
            final int index = i;
            bindings[i] = button(tr(labels[i], mappings[i].getTranslatedKeyMessage()), x, 82 + 36 * i, w - 66, () -> {
                waiting = index; refresh();
            });
            button(tr(i == 0 ? "unbind" : "unbind_place"), x + w - 60, 82 + 36 * i, 60,
                    () -> bind(index, InputConstants.UNKNOWN));
        }
        refresh();
        button(tr("back"), x, height - 29, w, this::onClose);
    }
    private void refresh() {
        for (int i = 0; i < bindings.length; i++)
            bindings[i].setMessage(waiting == i ? tr("press_key") : tr(labels[i], mappings[i].getTranslatedKeyMessage()));
    }
    private void bind(int index, InputConstants.Key key) {
        mappings[index].setKey(key); KeyMapping.resetMapping(); minecraft.options.save(); waiting = -1; refresh();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (waiting >= 0) {
            bind(waiting, event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(event.key()));
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (waiting >= 0) { bind(waiting, InputConstants.Type.MOUSE.getOrCreate(event.button())); return true; }
        return super.mouseClicked(event, twice);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        int w = Math.min(420, width - 30), left = (width - w) / 2;
        g.textWithWordWrap(font, tr("keys_hint"), left, 37, w, MUTED);
        if (waiting >= 0) g.textWithWordWrap(font, tr("escape_unbind"), left, 164, w, ACCENT);
        for (int i = 0; i < mappings.length; i++) {
            if (mappings[i].isUnbound()) continue;
            for (var key : minecraft.options.keyMappings) {
                if (key != mappings[i] && key.same(mappings[i])) {
                    text(g, tr("key_conflict", Component.translatable(key.getName())), left, 105 + 36 * i, w, 0xFFFFBB77); break;
                }
            }
        }
    }
}
