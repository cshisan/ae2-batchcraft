package cn.ae2bc.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** AE2 tab button that scales only its centered icon. */
final class ScaledTabButton extends TabButton {
    private final Icon icon;
    private final float iconScale;

    ScaledTabButton(Icon icon, float iconScale, Component message, OnPress onPress) {
        super(icon, message, onPress);
        this.icon = icon;
        this.iconScale = iconScale;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        Icon background;
        int iconOffsetX;
        switch (getStyle()) {
            case CORNER:
                background = isFocused() ? Icon.TAB_BUTTON_BACKGROUND_BORDERLESS_FOCUS
                        : Icon.TAB_BUTTON_BACKGROUND_BORDERLESS;
                iconOffsetX = 4;
                break;
            case BOX:
                background = isFocused() ? Icon.TAB_BUTTON_BACKGROUND_FOCUS
                        : Icon.TAB_BUTTON_BACKGROUND;
                iconOffsetX = 3;
                break;
            case HORIZONTAL:
                // This button is a mode switch, not a persistent tab selection. Keep the
                // horizontal toolbar background unchanged after it receives focus.
                background = Icon.HORIZONTAL_TAB;
                iconOffsetX = 1;
                break;
            default:
                throw new IllegalStateException("Unhandled tab style: " + getStyle());
        }
        background.getBlitter().dest(getX(), getY()).blit(graphics);
        int size = Math.max(1, Math.round(16 * iconScale));
        int x = getX() + iconOffsetX + (16 - size) / 2;
        int y = getY() + 3 + (16 - size) / 2;
        icon.getBlitter().dest(x, y, size, size).blit(graphics);
    }
}
