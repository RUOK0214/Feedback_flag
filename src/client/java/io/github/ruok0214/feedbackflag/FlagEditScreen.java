package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
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
    private String textValue, colorValue, memoValue;
    private boolean enabled, blocks, properties;
    private EditBox nameBox, colorBox;
    private MultiLineEditBox memoBox;
    private Button saveButton;
    private int left, panelWidth, settingsWidth, memoLeft, memoWidth;

    FlagEditScreen(Screen parent, FlagManager manager, UUID id) {
        super("edit_title", parent); this.manager = manager; this.id = id;
        var flag = manager.book().flag(id);
        textValue = flag.text(); colorValue = String.format(Locale.ROOT, "%06X", flag.color());
        memoValue = flag.memo();
        enabled = flag.enabled(); blocks = flag.watchBlocks(); properties = flag.watchProperties();
    }
    @Override protected void init() {
        panelWidth = Math.min(720, width - 24); left = (width - panelWidth) / 2;
        settingsWidth = (panelWidth - 12) / 2; memoLeft = left + settingsWidth + 12;
        memoWidth = panelWidth - settingsWidth - 12;
        nameBox = addRenderableWidget(new EditBox(font, left, 44, settingsWidth, 20, tr("text")));
        nameBox.setMaxLength(64); nameBox.setValue(textValue);
        nameBox.setResponder(value -> textValue = value);
        colorBox = addRenderableWidget(new EditBox(font, left, 84, 74, 20, tr("color")));
        colorBox.setMaxLength(7); colorBox.setValue(colorValue);
        colorBox.setResponder(value -> { colorValue = value; updateSave(); });
        int swatchWidth = Math.min(26, (settingsWidth - 82) / COLORS.length);
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            var swatch = button(Component.literal("■").withColor(color), left + 82 + i * swatchWidth, 84, swatchWidth - 2,
                    () -> colorBox.setValue(String.format(Locale.ROOT, "%06X", color)));
            swatch.setTooltip(Tooltip.create(Component.literal(String.format(Locale.ROOT, "#%06X", color))));
        }
        int half = (panelWidth - 6) / 2;
        addRenderableWidget(Button.builder(tr("individual", state(enabled)), b -> {
            enabled = !enabled; b.setMessage(tr("individual", state(enabled)));
        }).bounds(left, 112, settingsWidth, 20).build());
        int settingHalf = (settingsWidth - 6) / 2;
        addRenderableWidget(Button.builder(tr("watch_blocks", state(blocks)), b -> {
            blocks = !blocks; b.setMessage(tr("watch_blocks", state(blocks)));
        }).bounds(left, 138, settingHalf, 20).build());
        addRenderableWidget(Button.builder(tr("watch_states", state(properties)), b -> {
            properties = !properties; b.setMessage(tr("watch_states", state(properties)));
        }).bounds(left + settingHalf + 6, 138, settingsWidth - settingHalf - 6, 20).build());
        memoBox = addRenderableWidget(MultiLineEditBox.builder().setX(memoLeft).setY(44)
                .setPlaceholder(tr("memo_placeholder")).setShowBackground(true).setShowDecorations(false)
                .build(font, memoWidth, Math.max(42, height - 114), tr("memo_title")));
        memoBox.setCharacterLimit(FlagBook.MAX_MEMO * 2);
        memoBox.setValue(memoValue);
        memoBox.setValueListener(value -> { memoValue = value; updateSave(); });
        button(tr("flag_history"), left, height - 54, half,
                () -> minecraft.gui.setScreen(new DashboardScreen(manager, id)));
        button(tr("delete"), left + half + 6, height - 54, panelWidth - half - 6, () -> {
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) { manager.book().remove(id); manager.save(); minecraft.gui.setScreen(parent); }
                else minecraft.gui.setScreen(this);
            }, tr("delete_title"), tr("delete_hint")));
        });
        saveButton = button(tr("save"), left, height - 28, half, () -> {
            if (!validColor() || memoLength() > FlagBook.MAX_MEMO) return;
            manager.book().edit(id, textValue, memoValue, Integer.parseInt(colorValue.replace("#", ""), 16), enabled, blocks, properties);
            manager.save(); onClose();
        });
        updateSave();
        button(Component.translatable("gui.cancel"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
    }
    private boolean validColor() { return colorValue.matches("#?[0-9a-fA-F]{6}"); }
    private int memoLength() {
        String normalized = FlagBook.normalizeMemo(memoValue);
        return normalized.codePointCount(0, normalized.length());
    }
    private void updateSave() { if (saveButton != null) saveButton.active = validColor() && memoLength() <= FlagBook.MAX_MEMO; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        text(g, tr("text"), left, 31, settingsWidth, MUTED);
        text(g, tr(validColor() ? "color" : "invalid_color"), left, 71, settingsWidth, validColor() ? MUTED : 0xFFFF8888);
        text(g, tr("memo_title"), memoLeft, 31, memoWidth, WHITE);
        text(g, tr("memo_length", memoLength(), FlagBook.MAX_MEMO), memoLeft, height - 67, memoWidth,
                memoLength() <= FlagBook.MAX_MEMO ? MUTED : 0xFFFF8888);
        var flag = manager.book().flag(id);
        if (flag != null) text(g, tr("flag_summary", flag.target().coordinates(), tr(manager.status(flag)), flag.total()), left, 168, settingsWidth, MUTED);
        if (height >= 278) {
            int rgb = validColor() ? Integer.parseInt(colorValue.replace("#", ""), 16) : 0xFFFFFF;
            g.fill(left + 2, 193, left + 5, 219, 0xFF000000 | rgb);
            g.fill(left + 5, 193, left + 24, 206, 0xFF000000 | rgb);
            text(g, Component.literal(FlagBook.cleanText(textValue)), left + 32, 199, settingsWidth - 36, 0xFF000000 | rgb);
        }
    }
}
