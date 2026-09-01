package cn.ae2bc.client;

import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class VerticallyAlignedCheckbox extends AECheckbox {
    private static final int CHECKBOX_WIDTH = 22;
    private static final int LABEL_GAP = 4;

    private final ScreenStyle style;
    private final boolean showLabel;

    VerticallyAlignedCheckbox(ScreenStyle style, Component message) {
        this(style, message, true);
    }

    VerticallyAlignedCheckbox(ScreenStyle style, Component message, boolean showLabel) {
        super(0, 0, 0, SIZE, style, message);
        this.style = style;
        this.showLabel = showLabel;
    }

    int fitToMessage() {
        int labelWidth = showLabel
                ? Minecraft.getInstance().font.width(getMessage()) + LABEL_GAP
                : 0;
        int width = labelWidth + CHECKBOX_WIDTH;
        setWidth(width);
        return width;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Component message = getMessage();
        var font = Minecraft.getInstance().font;
        int labelX = getX();
        int checkboxX = showLabel ? labelX + font.width(message) + LABEL_GAP : labelX;
        boolean hovered = isMouseOver(mouseX, mouseY);

        setMessage(Component.empty());
        setX(checkboxX);
        try {
            super.renderWidget(graphics, hovered ? checkboxX : mouseX, mouseY, partialTick);
        } finally {
            setX(labelX);
            setMessage(message);
        }

        if (showLabel) {
            int color = style.getColor(isActive()
                    ? PaletteColor.DEFAULT_TEXT_COLOR
                    : PaletteColor.MUTED_TEXT_COLOR).toARGB();
            graphics.drawString(font, message, labelX, getY() + 2, color, false);
        }
    }
}
