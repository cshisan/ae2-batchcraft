package org.ae2bc.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public abstract class Pp2mpFilterScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    protected Pp2mpFilterScreen(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        ScreenHeader.addTo(this.widgets);
        this.widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE), this::getUpgradePanelTooltip));
    }

    protected List<Component> getUpgradePanelTooltip() {
        return List.of(GuiText.CompatibleUpgrades.text(), AEItems.INVERTER_CARD.stack().getHoverName());
    }

    /* 过滤界面统一绘制标记槽及玩家库存的 AE2 原生槽位背景。 */
    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        for (SlotSemantic semantic : List.of(SlotSemantics.CONFIG, SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR)) {
            SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(semantic));
        }
    }
}
