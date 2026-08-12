package cn.ae2bc.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class DashedSectionRenderer {
    private static final int TITLE_X = 18;
    private static final int DASH_LENGTH = 5;
    private static final int DASH_STEP = 6;
    private static final int COLOR = 0xFF808080;

    private DashedSectionRenderer() {
    }

    static void drawBackground(GuiGraphics graphics, Font font, Component title,
                               int offsetX, int offsetY, int imageWidth, int top, int bottom) {
        drawBackground(graphics, font, title, null, offsetX, offsetY, imageWidth, top, bottom);
    }

    static void drawBackground(GuiGraphics graphics, Font font, Component title, Component trailingTitle,
                               int offsetX, int offsetY, int imageWidth, int top, int bottom) {
        int left = offsetX + 7;
        int right = offsetX + imageWidth - 7;
        int absoluteTop = offsetY + top;
        int absoluteBottom = offsetY + bottom;
        int titleEnd = offsetX + TITLE_X + font.width(title);

        drawTopLeftCorner(graphics, left, absoluteTop);
        drawTopRightCorner(graphics, right, absoluteTop);
        drawBottomLeftCorner(graphics, left, absoluteBottom);
        drawBottomRightCorner(graphics, right, absoluteBottom);
        drawDashedHorizontal(graphics, left + 5, offsetX + TITLE_X - 2, absoluteTop);
        if (trailingTitle == null) {
            drawDashedHorizontal(graphics, titleEnd + 4, right - 5, absoluteTop);
        } else {
            int trailingStart = offsetX + trailingContentX(imageWidth, font.width(trailingTitle));
            int trailingEnd = trailingStart + font.width(trailingTitle);
            drawDashedHorizontal(graphics, titleEnd + 4, trailingStart - 4, absoluteTop);
            drawDashedHorizontal(graphics, trailingEnd + 4, right - 5, absoluteTop);
        }
        drawDashedVertical(graphics, left, absoluteTop + 5, absoluteBottom - 5);
        drawDashedVertical(graphics, right, absoluteTop + 5, absoluteBottom - 5);
        drawDashedHorizontal(graphics, left + 5, right - 5, absoluteBottom);
    }

    static void drawBackground(GuiGraphics graphics, Font font, Component title, int trailingReservedWidth,
                               int offsetX, int offsetY, int imageWidth, int top, int bottom) {
        int left = offsetX + 7;
        int right = offsetX + imageWidth - 7;
        int absoluteTop = offsetY + top;
        int absoluteBottom = offsetY + bottom;
        int titleEnd = offsetX + TITLE_X + font.width(title);
        int trailingStart = offsetX + trailingContentX(imageWidth, trailingReservedWidth);

        drawTopLeftCorner(graphics, left, absoluteTop);
        drawTopRightCorner(graphics, right, absoluteTop);
        drawBottomLeftCorner(graphics, left, absoluteBottom);
        drawBottomRightCorner(graphics, right, absoluteBottom);
        drawDashedHorizontal(graphics, left + 5, offsetX + TITLE_X - 2, absoluteTop);
        drawDashedHorizontal(graphics, titleEnd + 4, trailingStart - 4, absoluteTop);
        drawDashedHorizontal(graphics, trailingStart + trailingReservedWidth + 4, right - 5, absoluteTop);
        drawDashedVertical(graphics, left, absoluteTop + 5, absoluteBottom - 5);
        drawDashedVertical(graphics, right, absoluteTop + 5, absoluteBottom - 5);
        drawDashedHorizontal(graphics, left + 5, right - 5, absoluteBottom);
    }

    static void drawTitle(GuiGraphics graphics, Font font, Component title, int y) {
        graphics.drawString(font, title, TITLE_X, y, 0xFF404040, false);
    }

    static void drawTitle(GuiGraphics graphics, Font font, Component title, Component trailingTitle,
                          int y, int imageWidth) {
        drawTitle(graphics, font, title, y);
        int trailingX = trailingContentX(imageWidth, font.width(trailingTitle));
        graphics.drawString(font, trailingTitle, trailingX, y, 0xFF404040, false);
    }

    static int trailingContentX(int imageWidth, int width) {
        return imageWidth - TITLE_X - Math.max(0, width);
    }

    private static void drawDashedHorizontal(GuiGraphics graphics, int left, int right, int y) {
        for (int x = left; x <= right; x += DASH_STEP) {
            graphics.hLine(x, Math.min(x + DASH_LENGTH - 1, right), y, COLOR);
        }
    }

    private static void drawDashedVertical(GuiGraphics graphics, int x, int top, int bottom) {
        for (int y = top; y <= bottom; y += DASH_STEP) {
            graphics.vLine(x, y, Math.min(y + DASH_LENGTH - 1, bottom), COLOR);
        }
    }

    private static void drawTopLeftCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x, x + 3, y, COLOR);
        graphics.vLine(x, y, y + 3, COLOR);
    }

    private static void drawTopRightCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x - 3, x, y, COLOR);
        graphics.vLine(x, y, y + 3, COLOR);
    }

    private static void drawBottomLeftCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x, x + 3, y, COLOR);
        graphics.vLine(x, y - 3, y, COLOR);
    }

    private static void drawBottomRightCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x - 3, x, y, COLOR);
        graphics.vLine(x, y - 3, y, COLOR);
    }
}
