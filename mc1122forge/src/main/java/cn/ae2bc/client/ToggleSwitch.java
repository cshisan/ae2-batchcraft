package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Sliding boolean switch used by the 1.12.2 configuration screens. */
final class ToggleSwitch extends GuiButton {
    static final int LABEL_OFFSET = 26;
    private boolean selected;

    ToggleSwitch(int id, int x, int y, int width, String text, boolean selected) {
        super(id, x, y, width, 14, text);
        this.selected = selected;
    }

    boolean selected() {
        return selected;
    }

    boolean isHovered() {
        return hovered;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        if (!super.mousePressed(minecraft, mouseX, mouseY)) return false;
        selected = !selected;
        return true;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        int trackY = y + 1;
        int border = !enabled ? 0xff3a3a3a : hovered ? 0xffd0d0d0 : 0xff777777;
        int track = !enabled ? 0xff252525 : selected ? 0xff244f6b : 0xff3f3f3f;
        int inner = !enabled ? 0xff303030 : selected ? 0xff347299 : hovered ? 0xff5b5b5b : 0xff4b4b4b;
        drawRect(x, trackY, x + 22, trackY + 12, border);
        drawRect(x + 1, trackY + 1, x + 21, trackY + 11, track);
        drawRect(x + 2, trackY + 2, x + 20, trackY + 3, inner);
        int knobX = selected ? x + 12 : x + 2;
        int knobBorder = !enabled ? 0xff555555 : hovered ? 0xffffffff : 0xffa0a0a0;
        int knob = !enabled ? 0xff6b6b6b : 0xffd8d8d8;
        drawRect(knobX, trackY + 2, knobX + 8, trackY + 10, knobBorder);
        drawRect(knobX + 1, trackY + 3, knobX + 7, trackY + 9, knob);
        int textColor = enabled ? (hovered ? 0xff202020 : 0xff404040) : 0xff8a8a8a;
        minecraft.fontRenderer.drawString(displayString, x + LABEL_OFFSET, y + 3, textColor);
    }
}
