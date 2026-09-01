package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;

final class Ae2GuiSkin extends AbstractGui {
    private static final ResourceLocation INTERFACE =
            new ResourceLocation("appliedenergistics2", "textures/guis/interface.png");
    private static final ResourceLocation RIGHT_TOOLBAR =
            new ResourceLocation("appliedenergistics2", "textures/guis/extra_panels.png");
    private static final ResourceLocation STATES =
            new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 212;
    private static final int BORDER = 4;

    private Ae2GuiSkin() {
    }

    static void draw(MatrixStack matrices, int x, int y, int width, int height) {
        Minecraft.getInstance().getTextureManager().bind(INTERFACE);
        stretch(matrices, x + BORDER, y + BORDER, width - BORDER * 2, height - BORDER * 2,
                8, 8, 1, 1);

        stretch(matrices, x + BORDER, y, width - BORDER * 2, BORDER,
                BORDER, 0, PANEL_WIDTH - BORDER * 2, BORDER);
        stretch(matrices, x + BORDER, y + height - BORDER, width - BORDER * 2, BORDER,
                BORDER, PANEL_HEIGHT - BORDER, PANEL_WIDTH - BORDER * 2, BORDER);
        stretch(matrices, x, y + BORDER, BORDER, height - BORDER * 2,
                0, BORDER, BORDER, PANEL_HEIGHT - BORDER * 2);
        stretch(matrices, x + width - BORDER, y + BORDER, BORDER, height - BORDER * 2,
                PANEL_WIDTH - BORDER, BORDER, BORDER, PANEL_HEIGHT - BORDER * 2);

        stretch(matrices, x, y, BORDER, BORDER, 0, 0, BORDER, BORDER);
        stretch(matrices, x + width - BORDER, y, BORDER, BORDER,
                PANEL_WIDTH - BORDER, 0, BORDER, BORDER);
        stretch(matrices, x, y + height - BORDER, BORDER, BORDER,
                0, PANEL_HEIGHT - BORDER, BORDER, BORDER);
        stretch(matrices, x + width - BORDER, y + height - BORDER, BORDER, BORDER,
                PANEL_WIDTH - BORDER, PANEL_HEIGHT - BORDER, BORDER, BORDER);
    }

    static void drawRightToolbar(MatrixStack matrices, int x, int y, int mainWidth, int buttonCount) {
        int height = buttonCount * 20 + Math.max(0, buttonCount - 1) * 6 + 12;
        Minecraft.getInstance().getTextureManager().bind(RIGHT_TOOLBAR);
        stretch(matrices, x + mainWidth, y + 17, 28, height,
                0, 0, 32, 25);
    }

    static void drawDisabledToolbarButton(MatrixStack matrices, int x, int y) {
        Minecraft.getInstance().getTextureManager().bind(STATES);
        stretch(matrices, x, y, 20, 20, 240, 240, 16, 16);
    }

    private static void stretch(MatrixStack matrices, int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        blit(matrices, x, y, width, height, u, v, sourceWidth, sourceHeight,
                TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
