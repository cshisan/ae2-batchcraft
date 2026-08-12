package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;

final class DashedSectionRenderer extends AbstractGui {
    private static final int COLOR = 0xFF808080;
    private static final int TITLE_X = 18;

    private DashedSectionRenderer() {
    }

    static void draw(MatrixStack matrices, FontRenderer font, String title,
            int offsetX, int offsetY, int imageWidth, int top, int bottom) {
        draw(matrices, font, title, 0, offsetX, offsetY, imageWidth, top, bottom);
    }

    static void draw(MatrixStack matrices, FontRenderer font, String title, int trailingReservedWidth,
            int offsetX, int offsetY, int imageWidth, int top, int bottom) {
        int left = offsetX + 7;
        int right = offsetX + imageWidth - 7;
        int y1 = offsetY + top;
        int y2 = offsetY + bottom;
        int titleStart = offsetX + TITLE_X - 2;
        int titleEnd = offsetX + TITLE_X + font.width(title) + 4;
        horizontal(matrices, left, titleStart, y1);
        if (trailingReservedWidth > 0) {
            int trailingStart = offsetX + trailingContentX(imageWidth, trailingReservedWidth);
            horizontal(matrices, titleEnd, trailingStart - 4, y1);
            horizontal(matrices, trailingStart + trailingReservedWidth + 4, right, y1);
        } else {
            horizontal(matrices, titleEnd, right, y1);
        }
        horizontal(matrices, left, right, y2);
        vertical(matrices, left, y1, y2);
        vertical(matrices, right, y1, y2);
    }

    static void title(MatrixStack matrices, FontRenderer font, String title, int y) {
        font.draw(matrices, title, TITLE_X, y, 0x404040);
    }

    static int trailingContentX(int imageWidth, int width) {
        return imageWidth - TITLE_X - Math.max(0, width);
    }

    private static void horizontal(MatrixStack matrices, int left, int right, int y) {
        for (int x = left; x < right; x += 6) {
            fill(matrices, x, y, Math.min(x + 5, right), y + 1, COLOR);
        }
    }

    private static void vertical(MatrixStack matrices, int x, int top, int bottom) {
        for (int y = top; y < bottom; y += 6) {
            fill(matrices, x, y, x + 1, Math.min(y + 5, bottom), COLOR);
        }
    }
}
