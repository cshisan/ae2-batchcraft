package cn.ae2bc.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.client.gui.GuiGraphics;

/**
 * AE2's icon button with an independently scaled, centered icon.
 * The button background and hitbox retain the original IconButton dimensions.
 */
class ScaledIconButton extends IconButton {
    private final Icon icon;
    private final float iconScale;
    private final int iconOffsetX;
    private final int iconOffsetY;
    private boolean dimWhenInactive = true;

    ScaledIconButton(Icon icon, float iconScale, OnPress onPress) {
        this(icon, iconScale, 0, 0, onPress);
    }

    ScaledIconButton(Icon icon, float iconScale, int iconOffsetX, int iconOffsetY, OnPress onPress) {
        super(onPress);
        this.icon = icon;
        this.iconScale = iconScale;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    void setDimWhenInactive(boolean dimWhenInactive) {
        this.dimWhenInactive = dimWhenInactive;
    }

    @Override
    protected Icon getIcon() {
        return icon;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }

        var item = getItemOverlay();
        boolean halfSize = isHalfSize();
        int iconBoxSize = halfSize ? 8 : 16;
        if (halfSize) {
            setWidth(8);
            setHeight(8);
        }

        int yOffset = isHovered() ? 1 : 0;
        if (!isDisableBackground()) {
            Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                    : Icon.TOOLBAR_BUTTON_BACKGROUND;
            if (halfSize) {
                background.getBlitter().dest(getX(), getY()).zOffset(10).blit(graphics);
            } else {
                background.getBlitter()
                        .dest(getX() - 1, getY() + yOffset, 18, 20)
                        .zOffset(2)
                        .blit(graphics);
            }
        }

        if (item != null) {
            graphics.renderItem(new net.minecraft.world.item.ItemStack(item), getX(),
                    getY() + (halfSize ? 0 : 1 + yOffset), 0, halfSize ? 20 : 3);
            return;
        }

        Icon icon = getIcon();
        if (icon == null) {
            return;
        }

        int size = Math.max(1, Math.round(iconBoxSize * iconScale));
        int x = getX() + (iconBoxSize - size) / 2 + iconOffsetX;
        int y = getY() + (halfSize ? 0 : 1 + yOffset) + (iconBoxSize - size) / 2 + iconOffsetY;
        var blitter = icon.getBlitter();
        if (!active && dimWhenInactive) {
            blitter.opacity(0.5f);
        }
        blitter.dest(x, y, size, size).zOffset(halfSize ? 20 : 3).blit(graphics);
    }
}
