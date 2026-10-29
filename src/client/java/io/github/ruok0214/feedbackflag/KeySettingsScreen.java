package io.github.ruok0214.feedbackflag;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class KeySettingsScreen extends FlagScreen {
    private boolean waiting;
    private Button binding;
    KeySettingsScreen(Screen parent) { super("keys", parent); }
    @Override protected void init() {
        int w = Math.min(360, width - 30), x = (width - w) / 2;
        binding = button(tr("binding", FeedbackFlagClient.openMenu.getTranslatedKeyMessage()), x, 77, w, () -> {
            waiting = true; binding.setMessage(tr("press_key"));
        });
        button(tr("unbind"), x, 105, w, () -> bind(InputConstants.UNKNOWN));
        button(tr("back"), x, height - 29, w, this::onClose);
    }
    private void bind(InputConstants.Key key) {
        FeedbackFlagClient.openMenu.setKey(key); KeyMapping.resetMapping(); minecraft.options.save(); waiting = false;
        binding.setMessage(tr("binding", FeedbackFlagClient.openMenu.getTranslatedKeyMessage()));
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (waiting) {
            bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(event.key()));
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (waiting) { bind(InputConstants.Type.MOUSE.getOrCreate(event.button())); return true; }
        return super.mouseClicked(event, twice);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        int w = Math.min(360, width - 30), left = (width - w) / 2;
        g.textWithWordWrap(font, tr("keys_hint"), left, 37, w, MUTED);
        if (waiting) g.textWithWordWrap(font, tr("escape_unbind"), left, 138, w, ACCENT);
        else if (!FeedbackFlagClient.openMenu.isUnbound()) {
            for (var key : minecraft.options.keyMappings) {
                if (key != FeedbackFlagClient.openMenu && key.same(FeedbackFlagClient.openMenu)) {
                    g.textWithWordWrap(font, tr("key_conflict", net.minecraft.network.chat.Component.translatable(key.getName())), left, 138, w, 0xFFFFBB77); break;
                }
            }
        }
    }
}
