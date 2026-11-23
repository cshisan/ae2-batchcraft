package org.ae2bc.client.screen.component;

import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class TextButton extends ScrollingAE2Button {
    private static final int TEXT_PADDING = 2;
    private @Setter boolean selected;

    public TextButton(Component component, OnPress onPress) {
        super(component, onPress);
    }


    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        int color;
        if (this.selected) {
            color = 0xFF517497;
        } else if (this.isHovered()) {
            color = 0xFF808080;
        } else {
            color = 0xFF404040;
        }

        int left = this.getX() + TEXT_PADDING;
        int right = this.getX() + this.getWidth() - TEXT_PADDING;
        int bottom = this.getY() + this.getHeight();
        int textY = this.getY() + (this.getHeight() - font.lineHeight) / 2;
        this.renderLabel(graphics, font, TEXT_PADDING, color, textY);

        if (this.selected) {
            graphics.hLine(left, right - 1, bottom - 1, 0xFF517497);
        }
    }

}
