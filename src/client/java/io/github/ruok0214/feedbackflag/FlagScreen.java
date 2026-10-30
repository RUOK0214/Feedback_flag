package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class FlagScreen extends Screen {
    static final int WHITE = 0xFFF1F5F9, MUTED = 0xFFA6B4C6, ACCENT = 0xFF56C8E8;
    final Screen parent;
    FlagScreen(String title, Screen parent) { super(tr(title)); this.parent = parent; }
    static Component tr(String key, Object... arguments) { return Component.translatable("feedback_flag." + key, arguments); }
    static Component state(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    Button button(Component text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(text, ignored -> action.run()).bounds(x, y, w, 20).build());
    }
    String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
    void text(GuiGraphicsExtractor g, Component value, int x, int y, int maxWidth, int color) {
        g.text(font, fit(value.getString(), maxWidth), x, y, color);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xEC111923);
        g.fill(0, 0, width, 23, 0xF5233243);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.centeredText(font, title, width / 2, 8, WHITE);
    }
}
