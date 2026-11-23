package org.ae2bc.client.screen.component;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@Setter
@Getter
public class SelectableAE2Button extends ScrollingAE2Button {
    private boolean selected;

    public SelectableAE2Button(Component message, Runnable onPress) {
        super(message, ignored -> onPress.run());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        boolean highlighted = this.active && (this.selected || this.isHovered());
        graphics.blitSprite(
                SPRITES.get(this.active, highlighted),
                this.getX(), this.getY(), this.getWidth(), this.getHeight()
        );

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (!this.active) {
            this.renderButtonText(
                    graphics, minecraft.font, 2,
                    0x413F54 | Mth.ceil(this.alpha * 255.0F) << 24, -1
            );
        } else if (highlighted) {
            this.renderButtonText(
                    graphics, minecraft.font, 2,
                    0x517497 | Mth.ceil(this.alpha * 255.0F) << 24, 0
            );
        } else {
            this.renderButtonText(
                    graphics, minecraft.font, 2,
                    0xF2F2F2 | Mth.ceil(this.alpha * 255.0F) << 24, 1
            );
        }
    }
}
