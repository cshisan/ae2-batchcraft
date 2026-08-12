package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** Thin constructor adapter that keeps the screens independent of the platform constructor signature. */
final class Ae2Button extends GuiButton {
    Ae2Button(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        super.drawButton(minecraft, mouseX, mouseY, partialTicks);
        if (!visible || height >= 20) return;

        // Vanilla widgets.png is 20px high; restore its bottom border for compact buttons.
        minecraft.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableDepth();
        int textureY = 46 + getHoverState(hovered) * 20 + 19;
        drawTexturedModalRect(x, y + height - 1, 0, textureY, width / 2, 1);
        drawTexturedModalRect(x + width / 2, y + height - 1,
                200 - width / 2, textureY, width / 2, 1);
    }
}
