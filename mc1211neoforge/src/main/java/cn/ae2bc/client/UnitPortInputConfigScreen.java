package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** AE2-style material return filter screen for input-type unit ports. */
public final class UnitPortInputConfigScreen extends AEBaseScreen<UnitPortInputConfigMenu> {
    public UnitPortInputConfigScreen(UnitPortInputConfigMenu menu, Inventory inventory,
                                     Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        AESubScreen.addBackButton(menu, "back", widgets);
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        drawSlotBackgrounds(graphics, offsetX, offsetY, SlotSemantics.CONFIG);
        drawSlotBackgrounds(graphics, offsetX, offsetY, SlotSemantics.PLAYER_INVENTORY);
        drawSlotBackgrounds(graphics, offsetX, offsetY, SlotSemantics.PLAYER_HOTBAR);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        graphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
    }

    private void drawSlotBackgrounds(GuiGraphics graphics, int offsetX, int offsetY,
                                     SlotSemantic semantic) {
        for (Slot slot : menu.getSlots(semantic)) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(graphics);
        }
    }

    private List<Component> getCompatibleUpgrades() {
        return List.of(
                GuiText.CompatibleUpgrades.text(),
                AEItems.INVERTER_CARD.stack().getHoverName());
    }
}
