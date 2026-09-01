package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button.IPressable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Vanilla button with an icon from AE2's 16x16 states texture grid. */
class Ae2IconButton extends Ae2Button {
    private static final ResourceLocation STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");
    protected int iconIndex;

    Ae2IconButton(int x, int y, int iconIndex, IPressable pressed) {
        super(x, y, 20, 20, StringTextComponent.EMPTY, pressed);
        this.iconIndex = iconIndex;
    }

    void setIconIndex(int iconIndex) {
        this.iconIndex = iconIndex;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        // Icon-only toolbar buttons must not replace the icon with a confirmation label.
        ITextComponent message = getMessage();
        setMessage(StringTextComponent.EMPTY);
        super.renderButton(matrices, mouseX, mouseY, partialTick);
        setMessage(message);
        drawIcon(matrices, 1.0F);
    }

    protected void drawIcon(MatrixStack matrices, float opacity) {
        Minecraft.getInstance().getTextureManager().bind(STATES);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha * opacity);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(matrices, x + 2, y + 2, (iconIndex % 16) * 16, (iconIndex / 16) * 16,
                16, 16, 256, 256);
    }
}
