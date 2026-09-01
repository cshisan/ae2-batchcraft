package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button.IPressable;

/** Legacy equivalent of the AE2 toolbar button with a stable disabled state. */
final class DisabledStateAe2IconButton extends Ae2IconButton {
    DisabledStateAe2IconButton(int x, int y, int iconIndex, IPressable pressed) {
        super(x, y, iconIndex, pressed);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        if (!active) {
            Ae2GuiSkin.drawDisabledToolbarButton(matrices, x, y);
            drawIcon(matrices, 0.5F);
            return;
        }
        super.renderButton(matrices, mouseX, mouseY, partialTick);
    }
}
