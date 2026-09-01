package cn.ae2bc.client;

import net.minecraft.client.Minecraft;

/** Icon-only button used where the surrounding AE2 panel supplies the background. */
final class TransparentAe2IconButton extends Ae2IconButton {
    TransparentAe2IconButton(int id, int x, int y, int iconIndex) {
        super(id, x, y, iconIndex);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y
                && mouseX < x + width && mouseY < y + height;
        drawIcon(minecraft, enabled ? 1.0F : 0.5F);
    }
}
