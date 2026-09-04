package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Vanilla button with an icon from AE2's 16x16 states texture grid. */
class Ae2IconButton extends Ae2Button {
    // AE2's states.png is a 16-column grid. These indices match the 1.16.5 Icon enum.
    static final int PERMISSION_CRAFT = 178;
    static final int PERMISSION_BUILD = 179;
    static final int PERMISSION_BUILD_DISABLED = 195;
    static final int ARROW_RIGHT = 50;
    static final int ICON_128 = 128;
    static final int INVALID = 129;
    static final int FUZZY_PERCENT_99 = 99;
    // The legacy AE2 docs names are one tile earlier than the newer Icon enum names.
    static final int BLOCK_NO = 20;
    static final int BLOCK_YES = 21;
    private static final ResourceLocation STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");
    protected int iconIndex;
    private final float iconScale;
    private final int iconOffsetX;
    private final int iconOffsetY;

    Ae2IconButton(int id, int x, int y, int iconIndex) {
        this(id, x, y, iconIndex, 1.0F, 0, 0);
    }

    Ae2IconButton(int id, int x, int y, int iconIndex, float iconScale) {
        this(id, x, y, iconIndex, iconScale, 0, 0);
    }

    Ae2IconButton(int id, int x, int y, int iconIndex, float iconScale,
                  int iconOffsetX, int iconOffsetY) {
        super(id, x, y, 20, 20, "");
        this.iconIndex = iconIndex;
        this.iconScale = iconScale;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    void setIconIndex(int iconIndex) {
        this.iconIndex = iconIndex;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        // Icon-only toolbar buttons must not replace the icon with a confirmation label.
        String message = displayString;
        displayString = "";
        super.drawButton(minecraft, mouseX, mouseY, partialTicks);
        displayString = message;
        if (!visible) return;
        drawIcon(minecraft, 1.0F);
    }

    protected void drawIcon(Minecraft minecraft, float opacity) {
        GlStateManager.pushAttrib();
        minecraft.getTextureManager().bindTexture(STATES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, opacity);
        int offset = Math.round((16 - 16 * iconScale) / 2.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 2 + offset + iconOffsetX, y + 2 + offset + iconOffsetY, 0);
        GlStateManager.scale(iconScale, iconScale, 1.0F);
        drawTexturedModalRect(0, 0, (iconIndex % 16) * 16, (iconIndex / 16) * 16, 16, 16);
        GlStateManager.popMatrix();
        // Do not leak a disabled icon's alpha or blend state into later widgets.
        GlStateManager.popAttrib();
    }
}
