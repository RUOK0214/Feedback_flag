package io.github.ruok0214.feedbackflag;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

final class VisualSettingsScreen extends FlagScreen {
    private final FlagManager manager;
    private VisualSettings draft;
    private FlagBook.DisplayMode mode;
    private int transparency, left, panelWidth;
    VisualSettingsScreen(Screen parent, FlagManager manager) {
        super("visual_settings", parent); this.manager = manager;
        draft = manager.book().visuals(); mode = manager.book().displayMode(); transparency = manager.book().transparency();
    }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 24); left = (width - panelWidth) / 2;
        int half = (panelWidth - 6) / 2, right = left + half + 6;
        addRenderableWidget(Button.builder(modeLabel(), b -> { mode = mode.next(); b.setMessage(modeLabel()); })
                .bounds(left, 55, panelWidth, 20).build());
        addRenderableWidget(new SettingSlider(VisualSettings.Parameter.OUTLINE_WIDTH, left, 82, half));
        addRenderableWidget(new SettingSlider(VisualSettings.Parameter.FLAG_SCALE, right, 82, panelWidth - half - 6));
        addRenderableWidget(new SettingSlider(VisualSettings.Parameter.OFFSET_X, left, 108, half));
        addRenderableWidget(new SettingSlider(VisualSettings.Parameter.OFFSET_Z, right, 108, panelWidth - half - 6));
        addRenderableWidget(new SettingSlider(VisualSettings.Parameter.OFFSET_Y, left, 134, half));
        addRenderableWidget(new AbstractSliderButton(right, 134, panelWidth - half - 6, 20, Component.empty(), transparency / 100d) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(tr("transparency", (int)Math.round(value * 100))); }
            @Override protected void applyValue() { transparency = (int)Math.round(value * 100); }
        });
        addRenderableWidget(Button.builder(tr("visual.show_names", state(draft.showNames())), b -> {
            draft = draft.withVisibility(!draft.showNames(), draft.throughWalls());
            b.setMessage(tr("visual.show_names", state(draft.showNames())));
        }).bounds(left, 160, half, 20).build());
        addRenderableWidget(Button.builder(tr("visual.through_walls", state(draft.throughWalls())), b -> {
            draft = draft.withVisibility(draft.showNames(), !draft.throughWalls());
            b.setMessage(tr("visual.through_walls", state(draft.throughWalls())));
        }).bounds(right, 160, panelWidth - half - 6, 20).tooltip(Tooltip.create(tr("visual.through_walls_hint"))).build());
        button(tr("visual.defaults"), left, height - 54, panelWidth, () -> {
            draft = VisualSettings.DEFAULT; mode = FlagBook.DisplayMode.FLAGS; transparency = 15;
            minecraft.gui.setScreen(this);
        });
        button(tr("save"), left, height - 28, half, () -> {
            manager.book().setVisuals(draft); manager.book().setDisplayMode(mode); manager.book().setTransparency(transparency);
            manager.save(); onClose();
        });
        button(Component.translatable("gui.cancel"), right, height - 28, panelWidth - half - 6, this::onClose);
    }
    private Component modeLabel() { return tr("display", tr("display." + mode.name().toLowerCase(Locale.ROOT))); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.textWithWordWrap(font, tr("visual.hint"), left, 31, panelWidth, MUTED);
        if (height >= 290) g.textWithWordWrap(font, tr("visual.position_hint"), left, 193, panelWidth, MUTED);
    }
    private final class SettingSlider extends AbstractSliderButton {
        private final VisualSettings.Parameter parameter;
        SettingSlider(VisualSettings.Parameter parameter, int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), (draft.value(parameter) - parameter.min) / (parameter.max - parameter.min));
            this.parameter = parameter; updateMessage();
            setTooltip(Tooltip.create(tr(switch (parameter) {
                case OUTLINE_WIDTH -> "visual.outline_hint";
                case FLAG_SCALE -> "visual.scale_hint";
                default -> "visual.position_hint";
            })));
        }
        @Override protected void updateMessage() {
            double amount = draft.value(parameter);
            String number = parameter == VisualSettings.Parameter.FLAG_SCALE ? Integer.toString((int)Math.round(amount * 100))
                    : String.format(Locale.ROOT, parameter == VisualSettings.Parameter.OUTLINE_WIDTH ? "%.1f" : "%+.2f", amount);
            setMessage(tr("visual." + parameter.name().toLowerCase(Locale.ROOT), number));
        }
        @Override protected void applyValue() {
            double amount = parameter.min + Math.round(value * (parameter.max - parameter.min) / parameter.step) * parameter.step;
            draft = draft.withValue(parameter, amount);
        }
    }
}
