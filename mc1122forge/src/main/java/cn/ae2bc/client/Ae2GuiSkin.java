package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

final class Ae2GuiSkin extends Gui {
    private static final Ae2GuiSkin INSTANCE = new Ae2GuiSkin();
    private static final ResourceLocation INTERFACE =
            new ResourceLocation("appliedenergistics2", "textures/guis/interface.png");
    private static final ResourceLocation RIGHT_TOOLBAR =
            new ResourceLocation("appliedenergistics2", "textures/guis/bus.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 212;
    private static final int BORDER = 4;

    private Ae2GuiSkin() {
    }

    static void draw(int x, int y, int width, int height) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(INTERFACE);
        stretch(x + BORDER, y + BORDER, width - BORDER * 2, height - BORDER * 2,
                8, 8, 1, 1);

        stretch(x + BORDER, y, width - BORDER * 2, BORDER,
                BORDER, 0, PANEL_WIDTH - BORDER * 2, BORDER);
        stretch(x + BORDER, y + height - BORDER, width - BORDER * 2, BORDER,
                BORDER, PANEL_HEIGHT - BORDER, PANEL_WIDTH - BORDER * 2, BORDER);
        stretch(x, y + BORDER, BORDER, height - BORDER * 2,
                0, BORDER, BORDER, PANEL_HEIGHT - BORDER * 2);
        stretch(x + width - BORDER, y + BORDER, BORDER, height - BORDER * 2,
                PANEL_WIDTH - BORDER, BORDER, BORDER, PANEL_HEIGHT - BORDER * 2);

        stretch(x, y, BORDER, BORDER, 0, 0, BORDER, BORDER);
        stretch(x + width - BORDER, y, BORDER, BORDER,
                PANEL_WIDTH - BORDER, 0, BORDER, BORDER);
        stretch(x, y + height - BORDER, BORDER, BORDER,
                0, PANEL_HEIGHT - BORDER, BORDER, BORDER);
        stretch(x + width - BORDER, y + height - BORDER, BORDER, BORDER,
                PANEL_WIDTH - BORDER, PANEL_HEIGHT - BORDER, BORDER, BORDER);
    }

    static void drawRightToolbar(int x, int y, int mainWidth, int buttonCount) {
        int height = buttonCount * 20 + Math.max(0, buttonCount - 1) * 6 + 12;
        Minecraft.getMinecraft().getTextureManager().bindTexture(RIGHT_TOOLBAR);
        stretch(x + mainWidth, y + 17, 28, height, 176, 0, 35, 61);
    }

    /** Matches GuiUpgradeable's one-slot side panel used by the ME Interface. */
    static void drawInterfaceUpgradePanel(int x, int y, int mainWidth, int upgradeSlots) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(INTERFACE);
        INSTANCE.drawTexturedModalRect(x + mainWidth, y, 177, 0,
                35, 14 + Math.max(0, upgradeSlots) * 18);
    }

    private static void stretch(int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        drawScaledCustomSizeModalRect(x, y, u, v, sourceWidth, sourceHeight,
                width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
