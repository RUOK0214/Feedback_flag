package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.UUID;

public final class FlagEditScreen extends FlagScreen {
    private static final int[] COLORS = {0x56C8E8, 0x6EDD9B, 0xF2D16B, 0xF09B61, 0xEF758C, 0xBE9AF7, 0xFFFFFF, 0x8796AA};
    private final FlagManager manager;
    private final UUID id;
    private String textValue, colorValue;
    private boolean enabled, blocks, properties;
    private EditBox nameBox, colorBox;
    private Button saveButton;
    private int left, panelWidth;

    FlagEditScreen(Screen parent, FlagManager manager, UUID id) {
        super("edit_title", parent); this.manager = manager; this.id = id;
        var flag = manager.book().flag(id);
        textValue = flag.text(); colorValue = String.format(Locale.ROOT, "%06X", flag.color());
        enabled = flag.enabled(); blocks = flag.watchBlocks(); properties = flag.watchProperties();
    }
    @Override protected void init() {
        panelWidth = Math.min(440, width - 24); left = (width - panelWidth) / 2;
        nameBox = addRenderableWidget(new EditBox(font, left, 44, panelWidth, 20, tr("text")));
        nameBox.setMaxLength(64); nameBox.setValue(textValue);
        nameBox.setResponder(value -> textValue = value);
        colorBox = addRenderableWidget(new EditBox(font, left, 84, 74, 20, tr("color")));
        colorBox.setMaxLength(7); colorBox.setValue(colorValue);
        colorBox.setResponder(value -> { colorValue = value; if (saveButton != null) saveButton.active = validColor(); });
        int swatchWidth = Math.min(26, (panelWidth - 82) / COLORS.length);
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            var swatch = button(Component.literal("■").withColor(color), left + 82 + i * swatchWidth, 84, swatchWidth - 2,
                    () -> colorBox.setValue(String.format(Locale.ROOT, "%06X", color)));
            swatch.setTooltip(Tooltip.create(Component.literal(String.format(Locale.ROOT, "#%06X", color))));
        }
        addRenderableWidget(Button.builder(tr("individual", state(enabled)), b -> {
            enabled = !enabled; b.setMessage(tr("individual", state(enabled)));
        }).bounds(left, 112, panelWidth, 20).build());
        int half = (panelWidth - 6) / 2;
        addRenderableWidget(Button.builder(tr("watch_blocks", state(blocks)), b -> {
            blocks = !blocks; b.setMessage(tr("watch_blocks", state(blocks)));
        }).bounds(left, 138, half, 20).build());
        addRenderableWidget(Button.builder(tr("watch_states", state(properties)), b -> {
            properties = !properties; b.setMessage(tr("watch_states", state(properties)));
        }).bounds(left + half + 6, 138, panelWidth - half - 6, 20).build());
        button(tr("flag_history"), left, height - 54, half,
                () -> minecraft.gui.setScreen(new DashboardScreen(manager, id)));
        button(tr("delete"), left + half + 6, height - 54, panelWidth - half - 6, () -> {
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) { manager.book().remove(id); manager.save(); minecraft.gui.setScreen(parent); }
                else minecraft.gui.setScreen(this);
            }, tr("delete_title"), tr("delete_hint")));
        });
        saveButton = button(tr("save"), left, height - 28, half, () -> {
            if (!validColor()) return;
            manager.book().edit(id, textValue, Integer.parseInt(colorValue.replace("#", ""), 16), enabled, blocks, properties);
            manager.save(); onClose();
        });
        saveButton.active = validColor();
        button(Component.translatable("gui.cancel"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
    }
    private boolean validColor() { return colorValue.matches("#?[0-9a-fA-F]{6}"); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        text(g, tr("text"), left, 31, panelWidth, MUTED);
        text(g, tr(validColor() ? "color" : "invalid_color"), left, 71, panelWidth, validColor() ? MUTED : 0xFFFF8888);
        var flag = manager.book().flag(id);
        if (flag != null) text(g, tr("flag_summary", flag.target().coordinates(), tr(manager.status(flag)), flag.total()), left, 168, panelWidth, MUTED);
        if (height >= 278) {
            int rgb = validColor() ? Integer.parseInt(colorValue.replace("#", ""), 16) : 0xFFFFFF;
            g.fill(left + 2, 193, left + 5, 219, 0xFF000000 | rgb);
            g.fill(left + 5, 193, left + 24, 206, 0xFF000000 | rgb);
            text(g, Component.literal(FlagBook.cleanText(textValue)), left + 32, 199, panelWidth - 36, 0xFF000000 | rgb);
        }
    }
}
