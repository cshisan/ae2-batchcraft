package cn.ae2bc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;

/** Thin constructor adapter that keeps the screens independent of the platform constructor signature. */
final class Ae2Button extends Button {
    Ae2Button(int x, int y, int width, int height, ITextComponent message, IPressable pressed) {
        super(x, y, width, height, message, pressed);
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        super.renderButton(matrices, mouseX, mouseY, partialTick);
        if (height >= 20) return;

        // Vanilla widgets.png is 20px high; restore its bottom border for compact buttons.
        Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        int yImage = getYImage(isHovered());
        int textureY = 46 + yImage * 20 + 19;
        blit(matrices, x, y + height - 1, 0, textureY, width / 2, 1);
        blit(matrices, x + width / 2, y + height - 1,
                200 - width / 2, textureY, width / 2, 1);
    }
}
