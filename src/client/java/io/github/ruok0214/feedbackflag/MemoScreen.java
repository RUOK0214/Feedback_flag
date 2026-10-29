package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Edits the parent's draft. Only the flag settings Save button commits the draft to storage. */
final class MemoScreen extends FlagScreen {
    private final Consumer<String> apply;
    private String value;
    private MultiLineEditBox editor;
    private Button applyButton;
    private int left, panelWidth;

    MemoScreen(Screen parent, String initial, Consumer<String> apply) {
        super("memo_title", parent);
        this.value = initial;
        this.apply = apply;
    }
    @Override protected void init() {
        panelWidth = Math.min(540, width - 24); left = (width - panelWidth) / 2;
        editor = addRenderableWidget(MultiLineEditBox.builder().setX(left).setY(60)
                .setPlaceholder(tr("memo_placeholder")).setShowBackground(true).setShowDecorations(false)
                .build(font, panelWidth, Math.max(36, height - 112), tr("memo_title")));
        // The widget counts UTF-16 units; our counter counts Unicode code points, including emoji.
        editor.setCharacterLimit(FlagBook.MAX_MEMO * 2);
        editor.setValue(value);
        editor.setValueListener(next -> {
            value = next;
            if (applyButton != null) applyButton.active = length() <= FlagBook.MAX_MEMO;
        });
        int half = (panelWidth - 6) / 2;
        applyButton = button(tr("memo_apply"), left, height - 28, half, () -> {
            if (length() > FlagBook.MAX_MEMO) return;
            apply.accept(FlagBook.cleanMemo(value)); onClose();
        });
        applyButton.active = length() <= FlagBook.MAX_MEMO;
        button(Component.translatable("gui.cancel"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
        setInitialFocus(editor);
    }
    private int length() {
        String normalized = FlagBook.normalizeMemo(value);
        return normalized.codePointCount(0, normalized.length());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.textWithWordWrap(font, tr("memo_hint"), left, 33, panelWidth, MUTED);
        text(g, tr("memo_length", length(), FlagBook.MAX_MEMO), left, height - 43, panelWidth,
                length() <= FlagBook.MAX_MEMO ? MUTED : 0xFFFF8888);
    }
}
