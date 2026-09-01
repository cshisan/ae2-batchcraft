package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button.IPressable;

/** Pixel refresh icon with its own button background, without the right toolbar panel. */
final class RefreshAe2IconButton extends Ae2IconButton {
    RefreshAe2IconButton(int x, int y, IPressable pressed) {
        super(x, y, 0, pressed);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        super.renderButton(matrices, mouseX, mouseY, partialTick);
    }

    @Override
    protected void drawIcon(MatrixStack matrices, float opacity) {
        int color = !active ? 0xFF707070 : isHovered ? 0xFFFFFFFF : 0xFFB8B8B8;
        color = (color & 0x00FFFFFF) | (((int) (opacity * 255.0F) & 0xFF) << 24);
        int left = x + 2;
        int top = y + 2;

        // One continuous, hollow ring with a directional arrowhead at its end.
        fill(matrices, left + 6, top, left + 13, top + 2, color);
        fill(matrices, left + 13, top + 2, left + 16, top + 4, color);
        fill(matrices, left + 14, top + 4, left + 16, top + 10, color);
        fill(matrices, left + 12, top + 10, left + 14, top + 13, color);
        fill(matrices, left + 5, top + 12, left + 12, top + 14, color);
        fill(matrices, left + 3, top + 10, left + 5, top + 12, color);
        fill(matrices, left + 2, top + 5, left + 4, top + 10, color);
        fill(matrices, left + 4, top + 3, left + 6, top + 5, color);
        fill(matrices, left + 14, top + 2, left + 17, top + 5, color);
        fill(matrices, left + 12, top + 2, left + 15, top + 3, color);
    }
}
