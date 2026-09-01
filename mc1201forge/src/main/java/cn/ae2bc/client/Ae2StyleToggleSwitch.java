package cn.ae2bc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A 1.21-style sliding switch for the 1.20.1 AE2 GUI toolkit. */
class Ae2StyleToggleSwitch extends AbstractButton {
    static final int SWITCH_WIDTH = 22;
    static final int SWITCH_HEIGHT = 12;

    private boolean selected;
    private Runnable changeListener;

    Ae2StyleToggleSwitch(Component message) {
        super(0, 0, SWITCH_WIDTH, SWITCH_HEIGHT, message);
    }

    boolean isSelected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public void onPress() {
        selected = !selected;
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                createNarrationMessage());
        if (active) {
            narration.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE,
                    Component.translatable(isFocused()
                            ? "narration.checkbox.usage.focused"
                            : "narration.checkbox.usage.hovered"));
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        int border = !active ? 0xFF3A3A3A : hovered ? 0xFFD0D0D0 : 0xFF777777;
        int track = !active ? 0xFF252525 : selected ? 0xFF244F6B : 0xFF3F3F3F;
        int inner = !active ? 0xFF303030 : selected ? 0xFF347299 : hovered ? 0xFF5B5B5B : 0xFF4B4B4B;
        int knobBorder = !active ? 0xFF555555 : hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        int knob = !active ? 0xFF6B6B6B : 0xFFD8D8D8;
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + SWITCH_WIDTH - 1, y + SWITCH_HEIGHT - 1, track);
        graphics.fill(x + 2, y + 2, x + SWITCH_WIDTH - 2, y + 4, inner);
        int knobX = selected ? x + 12 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 8, y + 10, knobBorder);
        graphics.fill(knobX + 1, y + 3, knobX + 7, y + 9, knob);
    }
}
