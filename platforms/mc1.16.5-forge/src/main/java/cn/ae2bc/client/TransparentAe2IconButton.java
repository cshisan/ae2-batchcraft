package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button.IPressable;

/** Icon-only button used where the surrounding AE2 panel supplies the background. */
final class TransparentAe2IconButton extends Ae2IconButton {
    TransparentAe2IconButton(int x, int y, int iconIndex, IPressable pressed) {
        super(x, y, iconIndex, pressed);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        // Keep Widget's hover state current even though the toolbar omits the normal button background.
        isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        drawIcon(matrices, active ? 1.0F : 0.5F);
    }
}
