package cn.ae2bc.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.client.gui.GuiGraphics;

/**
 * An AE2 icon button that keeps its toolbar square visible while disabled.
 *
 * AE2's default IconButton uses the hover state even when {@code active} is
 * false. That is correct for ordinary inactive controls, but is misleading
 * for a configuration inherited from the global setting.
 */
abstract class DisabledStateIconButton extends IconButton {
    DisabledStateIconButton(OnPress onPress) {
        super(onPress);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }

        Icon background;
        int yOffset;
        if (!active) {
            background = Icon.TOOLBAR_BUTTON_BACKGROUND;
            yOffset = 0;
        } else {
            yOffset = isHovered() ? 1 : 0;
            background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                    : Icon.TOOLBAR_BUTTON_BACKGROUND;
        }
        background.getBlitter().dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2).blit(graphics);

        Icon icon = getIcon();
        if (!active) {
            icon.getBlitter().opacity(0.5f);
        }
        icon.getBlitter().dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(graphics);
    }
}
