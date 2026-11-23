package org.ae2bc.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMenu;

import java.util.ArrayList;
import java.util.List;

public final class FakeCraftingConverterScreen extends AEBaseScreen<FakeCraftingConverterMenu> {
    private final List<Slot> upperSlots;
    private final List<Slot> lowerSlots;
    private final FakeCraftingMarkerRenderer markerRenderer;

    public FakeCraftingConverterScreen(
            FakeCraftingConverterMenu menu, Inventory inventory, Component title, ScreenStyle style
    ) {
        super(menu, inventory, title, style);
        ScreenHeader.addTo(this.widgets);
        this.upperSlots = this.menu.getSlots(FakeCraftingConverterMenu.INPUT_MARKERS);
        this.lowerSlots = this.menu.getSlots(FakeCraftingConverterMenu.OUTPUT_MARKERS);
        List<Slot> markerSlots = new ArrayList<>(this.upperSlots);
        markerSlots.addAll(this.lowerSlots);
        this.markerRenderer = new FakeCraftingMarkerRenderer(markerSlots);
    }

    /* 箭头随上下槽居中，避免槽位布局调整后仍沿用固定坐标。 */
    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        Blitter arrow = Icon.WHITE_ARROW_DOWN.getBlitter();
        int columns = Math.min(this.upperSlots.size(), this.lowerSlots.size());
        for (int column = 0; column < columns; column++) {
            Slot upperSlot = this.upperSlots.get(column);
            Slot lowerSlot = this.lowerSlots.get(column);
            int arrowX = upperSlot.x + (16 - arrow.getSrcWidth()) / 2;
            int arrowY = (upperSlot.y + 16 + lowerSlot.y - arrow.getSrcHeight()) / 2;
            arrow.dest(arrowX, arrowY).blit(graphics);
        }
    }

    @Override
    public void drawBG(
            GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks
    ) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.upperSlots);
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.lowerSlots);
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(SlotSemantics.PLAYER_HOTBAR));
        this.markerRenderer.render(graphics, offsetX, offsetY, this.imageWidth, this.imageHeight);
    }

    /* 界面关闭时释放标记绘图区，重新打开时按当前 GUI 缩放创建。 */
    @Override
    public void removed() {
        try {
            this.markerRenderer.close();
        } finally {
            super.removed();
        }
    }
}
