package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

final class BulkEditScreen extends FlagScreen {
    private final FlagManager manager;
    private final Set<UUID> selection;
    private boolean useColor;
    private String color = "56C8E8", tags = "";
    private BulkEdit.Switch enabled = BulkEdit.Switch.KEEP, blocks = BulkEdit.Switch.KEEP, states = BulkEdit.Switch.KEEP;
    private BulkEdit.Tags tagMode = BulkEdit.Tags.KEEP;
    private int left, panelWidth;
    private Button apply, colorToggle;
    private EditBox colorBox;
    private String error;
    BulkEditScreen(Screen parent, FlagManager manager, Set<UUID> selection) {
        super("bulk.title", parent); this.manager = manager; this.selection = Set.copyOf(selection);
    }
    @Override protected void init() {
        panelWidth = Math.min(600, width - 24); left = (width - panelWidth) / 2;
        colorToggle = button(tr("bulk.color", tr(useColor ? "bulk.change" : "bulk.keep")), left, 49, panelWidth - 88, () -> {
            useColor = !useColor; colorToggle.setMessage(tr("bulk.color", tr(useColor ? "bulk.change" : "bulk.keep"))); validate();
        });
        colorBox = addRenderableWidget(new EditBox(font, left + panelWidth - 82, 49, 82, 20, tr("search_color")));
        colorBox.setMaxLength(7); colorBox.setValue(color); colorBox.setResponder(v -> { color = v; validate(); });
        int cell = panelWidth / 16;
        for (int i = 0; i < BasicPalette.COLORS.size(); i++) {
            var c = BasicPalette.COLORS.get(i);
            addRenderableWidget(Button.builder(Component.literal("■").withColor(c.rgb()), b -> {
                useColor = true; colorToggle.setMessage(tr("bulk.color", tr("bulk.change"))); colorBox.setValue(String.format(Locale.ROOT, "%06X", c.rgb()));
            }).bounds(left + i * cell, 75, cell - 2, 18).tooltip(Tooltip.create(c.label())).build());
        }
        int third = (panelWidth - 8) / 3;
        addRenderableWidget(Button.builder(toggle("bulk.enabled", enabled), b -> { enabled = enabled.next(); b.setMessage(toggle("bulk.enabled", enabled)); }).bounds(left, 103, third, 20).build());
        addRenderableWidget(Button.builder(toggle("bulk.blocks", blocks), b -> { blocks = blocks.next(); b.setMessage(toggle("bulk.blocks", blocks)); }).bounds(left + third + 4, 103, third, 20).build());
        addRenderableWidget(Button.builder(toggle("bulk.states", states), b -> { states = states.next(); b.setMessage(toggle("bulk.states", states)); }).bounds(left + (third + 4) * 2, 103, third, 20).build());
        addRenderableWidget(Button.builder(tr("bulk.tags", tagLabel()), b -> { tagMode = tagMode.next(); b.setMessage(tr("bulk.tags", tagLabel())); validate(); }).bounds(left, 132, panelWidth, 20).tooltip(Tooltip.create(tr("bulk.tags_hint"))).build());
        var box = addRenderableWidget(new EditBox(font, left, 170, panelWidth, 20, tr("tags")));
        box.setMaxLength(512); box.setValue(tags); box.setResponder(v -> { tags = v; validate(); });
        int half = (panelWidth - 6) / 2;
        apply = button(tr("bulk.apply", selection.size()), left, height - 28, half, () -> {
            try { manager.book().editAll(selection, edit()); manager.save(); onClose(); }
            catch (IllegalArgumentException ex) { error = "bulk.invalid"; }
        });
        button(Component.translatable("gui.cancel"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
        validate();
    }
    private Component toggle(String key, BulkEdit.Switch value) { return tr(key, tr("bulk." + value.name().toLowerCase(Locale.ROOT))); }
    private Component tagLabel() { return tr("bulk.tags." + tagMode.name().toLowerCase(Locale.ROOT)); }
    private BulkEdit edit() { return new BulkEdit(useColor ? Integer.parseInt(color.replace("#", ""), 16) : null, enabled, blocks, states, tagMode, FlagBook.parseTags(tags)); }
    private void validate() {
        if (apply == null) return;
        boolean valid = !useColor || color.matches("#?[0-9a-fA-F]{6}");
        valid &= tagMode == BulkEdit.Tags.KEEP || FlagBook.validTags(tags);
        if (valid) try {
            var edit = edit();
            for (UUID id : selection) { var flag = manager.book().flag(id); if (flag == null) { valid = false; break; } edit.apply(flag); }
        } catch (IllegalArgumentException ex) { valid = false; }
        apply.active = valid && !selection.isEmpty(); error = valid ? null : "bulk.invalid";
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        text(g, tr("bulk.hint", selection.size()), left, 32, panelWidth, MUTED);
        text(g, tr("tags"), left, 157, panelWidth, MUTED);
        if (error != null) text(g, tr(error), left, height - 41, panelWidth, 0xFFFF8888);
    }
}
