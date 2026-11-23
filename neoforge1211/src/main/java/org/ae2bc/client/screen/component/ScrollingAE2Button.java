package org.ae2bc.client.screen.component;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class ScrollingAE2Button extends AE2Button {
    public ScrollingAE2Button(Component message, OnPress onPress) {
        super(message, onPress);
        this.setTooltip(Tooltip.create(message));
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.setTooltip(Tooltip.create(message));
    }

    @Override
    protected void renderButtonText(
            @NonNull GuiGraphics graphics, @NonNull Font font, int padding, int color, int yOffset
    ) {
        int textY = this.getY() + (this.getHeight() - font.lineHeight) / 2 + 1 - yOffset;
        this.renderLabel(graphics, font, padding, color, textY);
    }

    /* 超宽文字静态省略，完整文案通过悬浮提示查看。 */
    protected final void renderLabel(GuiGraphics graphics, Font font, int padding, int color, int textY) {
        int left = this.getX() + padding;
        int right = this.getX() + this.getWidth() - padding;
        int available = right - left;
        if (available <= 0) {
            return;
        }
        Component message = this.getMessage();
        int width = font.width(message);
        if (width > available) {
            int ellipsisWidth = font.width("...");
            if (ellipsisWidth < available) {
                int textWidth = available - ellipsisWidth;
                String head = font.plainSubstrByWidth(message.getString(), textWidth);
                message = Component.literal(head + "...");
            } else {
                message = Component.literal(font.plainSubstrByWidth(message.getString(), available));
            }
            width = font.width(message);
        }
        int textX = left + (available - width + 1) / 2;
        graphics.drawString(font, message, textX, textY, color, false);
    }
}
