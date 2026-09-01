package cn.ae2bc.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.client.gui.GuiGraphics;

/** Keeps the AE2 toolbar square visible without showing a disabled hover state. */
abstract class DisabledStateIconButton extends IconButton {
    DisabledStateIconButton(OnPress onPress) {
        super(onPress);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }

        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter()
                .dest(getX() - 1, getY(), 18, 20).blit(graphics);

        Icon icon = getIcon();
        if (!active) {
            icon.getBlitter().opacity(0.5f);
        }
        icon.getBlitter().dest(getX(), getY() + 1).blit(graphics);
    }
}
