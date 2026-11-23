package org.ae2bc.client.screen;

import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * 绘制 AE2 原生槽位背景及槽位组白色描边。
 */
public final class SlotGroupRenderer {
    private static final int BORDER_COLOR = 0xFFFFFFFF;

    private SlotGroupRenderer() {
    }

    /* 绘制槽位背景，再按启用槽位的范围绘制紧贴槽位的白边。 */
    public static void draw(GuiGraphics graphics, int offsetX, int offsetY, List<Slot> slots) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean hasActiveSlot = false;

        for (Slot slot : slots) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(graphics);
            if (!slot.isActive()) {
                continue;
            }
            hasActiveSlot = true;
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x);
            maxY = Math.max(maxY, slot.y);
        }

        if (!hasActiveSlot) {
            return;
        }

        int left = offsetX + minX - 1;
        int top = offsetY + minY - 1;
        int right = offsetX + maxX + 16;
        int bottom = offsetY + maxY + 16;
        graphics.hLine(left, right, top, BORDER_COLOR);
        graphics.hLine(left, right, bottom, BORDER_COLOR);
        graphics.vLine(left, top, bottom, BORDER_COLOR);
        graphics.vLine(right, top, bottom, BORDER_COLOR);
    }
}
