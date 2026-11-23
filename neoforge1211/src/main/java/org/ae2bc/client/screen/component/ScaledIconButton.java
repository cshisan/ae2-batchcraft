package org.ae2bc.client.screen.component;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.TextureTransform;
import appeng.client.gui.widgets.IconButton;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ScaledIconButton extends IconButton {
    private final float iconScale;
    private final int iconOffsetX;
    private final int iconOffsetY;
    private Icon ae2Icon;
    private Blitter customIcon;
    @Setter
    private boolean mirrored;
    @Setter
    private boolean selected;

    public ScaledIconButton(Icon icon, float iconScale, OnPress onPress) {
        this(icon, iconScale, 0, 0, onPress);
    }

    /* 使用独立的 16×16 贴图，复用 AE2 工具栏按钮的背景与交互。 */
    public ScaledIconButton(ResourceLocation texture, float iconScale, OnPress onPress) {
        this(texture, iconScale, 0, 0, onPress);
    }

    /* 仅调整自定义图案的绘制偏移，保持按钮背景和点击区域不变。 */
    public ScaledIconButton(ResourceLocation texture, float iconScale, int iconOffsetX, int iconOffsetY, OnPress onPress) {
        this((Icon) null, iconScale, iconOffsetX, iconOffsetY, onPress);
        this.customIcon = Blitter.texture(texture, 16, 16).src(0, 0, 16, 16);
    }

    public ScaledIconButton(Icon icon, float iconScale, int iconOffsetX, int iconOffsetY, OnPress onPress) {
        super(onPress);
        this.ae2Icon = icon;
        this.iconScale = iconScale;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    public void setAe2Icon(Icon icon) {
        this.ae2Icon = icon;
        this.customIcon = null;
    }

    public void setCustomIcon(ResourceLocation texture) {
        this.ae2Icon = null;
        this.customIcon = Blitter.texture(texture, 16, 16).src(0, 0, 16, 16);
    }

    @Override
    protected Icon getIcon() {
        return this.ae2Icon;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }

        Item item = this.getItemOverlay();
        boolean halfSize = this.isHalfSize();
        int iconBoxSize = halfSize ? 8 : 16;
        if (halfSize) {
            this.setWidth(8);
            this.setHeight(8);
        }

        int yOffset = this.isHovered() ? 1 : 0;
        if (!this.isDisableBackground()) {
            Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : this.selected || this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
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

        // 自定义贴图保持实际缩放比例，不进行偶数像素取整。
        if (this.customIcon != null) {
            float size = Math.max(1.0f, iconBoxSize * this.iconScale);
            float x = this.getX() + (iconBoxSize - size) / 2.0f + this.iconOffsetX;
            float y = this.getY() + (halfSize ? 0 : 1 + yOffset)
                    + (iconBoxSize - size) / 2.0f + this.iconOffsetY;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(size / 16.0f, size / 16.0f, 1.0f);
            this.customIcon.copy()
                    .transform(this.mirrored ? TextureTransform.MIRROR_H : TextureTransform.NONE)
                    .dest(0, 0, 16, 16).zOffset(halfSize ? 20 : 3).blit(graphics);
            graphics.pose().popPose();
            return;
        }

        int size = Math.max(1, Math.round(iconBoxSize * iconScale));
        // 让完整尺寸图标位于偶数像素网格，使整数居中时两侧边距一致。
        if (!halfSize && (size & 1) != 0) {
            size++;
        }
        int x = getX() + (iconBoxSize - size) / 2 + iconOffsetX;
        int y = getY() + (halfSize ? 0 : 1 + yOffset) + (iconBoxSize - size) / 2 + iconOffsetY;
        Blitter blitter = this.ae2Icon.getBlitter();
        blitter.transform(this.mirrored ? TextureTransform.MIRROR_H : TextureTransform.NONE)
                .dest(x, y, size, size).zOffset(halfSize ? 20 : 3).blit(graphics);
    }
}
