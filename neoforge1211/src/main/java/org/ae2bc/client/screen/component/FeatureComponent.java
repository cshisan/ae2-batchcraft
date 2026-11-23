package org.ae2bc.client.screen.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;

public class FeatureComponent {
    private static final int TITLE_X = 18;
    private static final int TITLE_OFFSET_Y = 4;
    private static final int BORDER_MARGIN_X = 7;
    private static final int CONTENT_PADDING_X = 4;
    private static final int CONTENT_TOP_PADDING = 9;
    private static final int CONTENT_BOTTOM_PADDING = 4;

    private static final int DASH_LENGTH = 5;
    private static final int DASH_STEP = 6;
    private static final int BORDER_COLOR = 0xFF808080;
    private static final int TITLE_COLOR = 0xFF404040;

    private final Component title;
    private final int top;
    private final int bottom;
    private final BiConsumer<GuiGraphics, ContentBounds> contentRenderer;

    public FeatureComponent(
            Component title, int top, int bottom,
            BiConsumer<GuiGraphics, ContentBounds> contentRenderer
    ) {
        if (bottom <= top) {
            throw new IllegalArgumentException("Section bottom must be greater than top");
        }

        this.title = Objects.requireNonNull(title);
        this.top = top;
        this.bottom = bottom;
        this.contentRenderer = contentRenderer;
    }

    public void drawBackground(GuiGraphics graphics, Font font, int offsetX, int offsetY, int imageWidth) {
        int left = offsetX + BORDER_MARGIN_X;
        int right = offsetX + imageWidth - BORDER_MARGIN_X;
        int absoluteTop = offsetY + this.top;
        int absoluteBottom = offsetY + this.bottom;

        int titleStart = offsetX + TITLE_X;
        int titleEnd = titleStart + font.width(this.title);

        this.drawTopLeftCorner(graphics, left, absoluteTop);
        this.drawTopRightCorner(graphics, right, absoluteTop);
        this.drawBottomLeftCorner(graphics, left, absoluteBottom);
        this.drawBottomRightCorner(graphics, right, absoluteBottom);

        this.drawDashedHorizontal(graphics, left + 5, titleStart - 2, absoluteTop);
        this.drawDashedHorizontal(graphics, titleEnd + 4, right - 5, absoluteTop);
        this.drawDashedVertical(graphics, left, absoluteTop + 5, absoluteBottom - 5);
        this.drawDashedVertical(graphics, right, absoluteTop + 5, absoluteBottom - 5);
        this.drawDashedHorizontal(graphics, left + 5, right - 5, absoluteBottom);
    }

    public void drawForeground(GuiGraphics graphics, Font font, int offsetX, int offsetY, int imageWidth) {
        ContentBounds bounds = this.getContentBounds(offsetX, offsetY, imageWidth);

        graphics.drawString(
                font, this.title, offsetX + TITLE_X,
                offsetY + this.top - TITLE_OFFSET_Y,
                TITLE_COLOR, false
        );

        if (contentRenderer != null) {
            contentRenderer.accept(graphics, bounds);
        }
    }

    public ContentBounds getContentBounds(int offsetX, int offsetY, int imageWidth) {
        int left = offsetX + BORDER_MARGIN_X;
        int right = offsetX + imageWidth - BORDER_MARGIN_X;
        int absoluteTop = offsetY + this.top;
        int absoluteBottom = offsetY + this.bottom;

        int contentLeft = left + CONTENT_PADDING_X;
        int contentTop = absoluteTop + CONTENT_TOP_PADDING;
        int contentWidth = right - left - CONTENT_PADDING_X * 2;
        int contentHeight = absoluteBottom - contentTop - CONTENT_BOTTOM_PADDING;

        return new ContentBounds(contentLeft, contentTop, Math.max(0, contentWidth), Math.max(0, contentHeight));
    }

    private void drawDashedHorizontal(GuiGraphics graphics, int left, int right, int y) {
        if (left > right) {
            return;
        }

        for (int x = left; x <= right; x += DASH_STEP) {
            graphics.hLine(x, Math.min(x + DASH_LENGTH - 1, right), y, BORDER_COLOR);
        }
    }

    private void drawDashedVertical(GuiGraphics graphics, int x, int top, int bottom) {
        if (top > bottom) {
            return;
        }

        for (int y = top; y <= bottom; y += DASH_STEP) {
            graphics.vLine(x, y, Math.min(y + DASH_LENGTH - 1, bottom), BORDER_COLOR);
        }
    }

    private void drawTopLeftCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x, x + 3, y, BORDER_COLOR);
        graphics.vLine(x, y, y + 3, BORDER_COLOR);
    }

    private void drawTopRightCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x - 3, x, y, BORDER_COLOR);
        graphics.vLine(x, y, y + 3, BORDER_COLOR);
    }

    private void drawBottomLeftCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x, x + 3, y, BORDER_COLOR);
        graphics.vLine(x, y - 3, y, BORDER_COLOR);
    }

    private void drawBottomRightCorner(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x - 3, x, y, BORDER_COLOR);
        graphics.vLine(x, y - 3, y, BORDER_COLOR);
    }

    public record ContentBounds(int left, int top, int width, int height) {
    }
}
