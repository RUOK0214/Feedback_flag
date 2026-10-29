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
    private final FlagManager manager;
    private final UUID id;
    private String textValue, colorValue, memoValue, tagsValue;
    private boolean enabled, blocks, properties;
    private EditBox nameBox, colorBox;
    private MultiLineEditBox memoBox;
    private Button saveButton;
    private int left, panelWidth, settingsWidth, memoLeft, memoWidth;

    FlagEditScreen(Screen parent, FlagManager manager, UUID id) {
        super("edit_title", parent); this.manager = manager; this.id = id;
        var flag = manager.book().flag(id);
        textValue = flag.text(); colorValue = String.format(Locale.ROOT, "%06X", flag.color());
        memoValue = flag.memo(); tagsValue = String.join(", ", flag.tags());
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
        int swatchWidth = Math.min(26, (settingsWidth - 82) / 8);
        for (int i = 0; i < ConcretePalette.COLORS.size(); i++) {
            var color = ConcretePalette.COLORS.get(i);
            var swatch = addRenderableWidget(Button.builder(Component.literal("■").withColor(color.rgb()),
                    ignored -> colorBox.setValue(String.format(Locale.ROOT, "%06X", color.rgb())))
                    .bounds(left + 82 + (i % 8) * swatchWidth, 82 + (i / 8) * 20, swatchWidth - 2, 16).build());
            swatch.setTooltip(Tooltip.create(color.label().copy().append(String.format(Locale.ROOT, " · #%06X", color.rgb()))));
        }
        int half = (panelWidth - 6) / 2;
        addRenderableWidget(Button.builder(tr("individual", state(enabled)), b -> {
            enabled = !enabled; b.setMessage(tr("individual", state(enabled)));
        }).bounds(left, 124, settingsWidth, 20).tooltip(Tooltip.create(tr("individual_hint"))).build());
        int settingHalf = (settingsWidth - 6) / 2;
        addRenderableWidget(Button.builder(tr("watch_blocks", state(blocks)), b -> {
            blocks = !blocks; b.setMessage(tr("watch_blocks", state(blocks)));
        }).bounds(left, 148, settingHalf, 20).tooltip(Tooltip.create(tr("watch_blocks_hint"))).build());
        addRenderableWidget(Button.builder(tr("watch_states", state(properties)), b -> {
            properties = !properties; b.setMessage(tr("watch_states", state(properties)));
        }).bounds(left + settingHalf + 6, 148, settingsWidth - settingHalf - 6, 20).tooltip(Tooltip.create(tr("watch_states_hint"))).build());
        var tagsBox = addRenderableWidget(new EditBox(font, memoLeft, 44, memoWidth, 20, tr("tags")));
        tagsBox.setMaxLength(512); tagsBox.setValue(tagsValue);
        tagsBox.setResponder(value -> { tagsValue = value; updateSave(); });
        tagsBox.setTooltip(Tooltip.create(tr("tags_hint")));
        memoBox = addRenderableWidget(MultiLineEditBox.builder().setX(memoLeft).setY(92)
                .setPlaceholder(tr("memo_placeholder")).setShowBackground(true).setShowDecorations(false)
                .build(font, memoWidth, Math.max(42, height - 162), tr("memo_title")));
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
            if (!validColor() || memoLength() > FlagBook.MAX_MEMO || !FlagBook.validTags(tagsValue)) return;
            manager.book().edit(id, textValue, memoValue, FlagBook.parseTags(tagsValue), Integer.parseInt(colorValue.replace("#", ""), 16), enabled, blocks, properties);
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
    private void updateSave() { if (saveButton != null) saveButton.active = validColor() && memoLength() <= FlagBook.MAX_MEMO && FlagBook.validTags(tagsValue); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        text(g, tr("text"), left, 31, settingsWidth, MUTED);
        text(g, tr(validColor() ? "color" : "invalid_color"), left, 71, settingsWidth, validColor() ? MUTED : 0xFFFF8888);
        text(g, tr("tags"), memoLeft, 31, memoWidth, MUTED);
        text(g, tr(FlagBook.validTags(tagsValue) ? "memo_title" : "invalid_tags"), memoLeft, 78, memoWidth,
                FlagBook.validTags(tagsValue) ? WHITE : 0xFFFF8888);
        text(g, tr("memo_length", memoLength(), FlagBook.MAX_MEMO), memoLeft, height - 67, memoWidth,
                memoLength() <= FlagBook.MAX_MEMO ? MUTED : 0xFFFF8888);
        var flag = manager.book().flag(id);
        if (flag != null) text(g, tr("flag_summary", flag.target().coordinates(), tr(manager.status(flag)), flag.total()), left, 176, settingsWidth, MUTED);
        if (height >= 278) {
            int rgb = validColor() ? Integer.parseInt(colorValue.replace("#", ""), 16) : 0xFFFFFF;
            g.fill(left + 2, 193, left + 5, 219, 0xFF000000 | rgb);
            g.fill(left + 5, 193, left + 24, 206, 0xFF000000 | rgb);
            text(g, Component.literal(FlagBook.cleanText(textValue)), left + 32, 199, settingsWidth - 36, 0xFF000000 | rgb);
        }
    }
}
