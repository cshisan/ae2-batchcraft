package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.client.gui.widgets.IconButton;
import appeng.core.localization.GuiText;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** AE2-style output-port priority and material filter screen. */
public final class UnitPortOutputConfigScreen extends AEBaseScreen<UnitPortOutputConfigMenu> {
    private final NumberEntryWidget priority;
    private final IconButton singleSlotToolbar;

    public UnitPortOutputConfigScreen(UnitPortOutputConfigMenu menu, Inventory inventory,
                                      Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        AESubScreen.addBackButton(menu, "back", widgets);
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));
        singleSlotToolbar = new DisabledStateIconButton(ignored ->
                menu.setSingleSlot(!menu.singleSlot)) {
            @Override
            protected Icon getIcon() {
                return menu.singleSlot ? Icon.BLOCKING_MODE_YES : Icon.BLOCKING_MODE_NO;
            }
        };
        updateSingleSlotTooltip();
        addToLeftToolbar(singleSlotToolbar);
        priority = widgets.addNumberEntryWidget("priority", NumberEntryType.UNITLESS);
        priority.setTextFieldStyle(style.getWidget("priorityInput"));
        priority.setMinValue(PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY);
        priority.setMaxValue(PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY);
        priority.setLongValue(menu.priority);
        priority.setOnChange(() -> priority.getIntValue().ifPresent(menu::setPriority));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        drawSlotBackgrounds(graphics, offsetX, offsetY, UnitPortOutputConfigMenu.MARKER_SLOT);
        drawSlotBackgrounds(graphics, offsetX, offsetY, SlotSemantics.PLAYER_INVENTORY);
        drawSlotBackgrounds(graphics, offsetX, offsetY, SlotSemantics.PLAYER_HOTBAR);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        graphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        singleSlotToolbar.active = menu.singleSlotEditable;
        updateSingleSlotTooltip();
    }

    private void updateSingleSlotTooltip() {
        singleSlotToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.unit_port_output_config.single_slot.tooltip",
                Component.translatable(menu.singleSlot
                        ? "gui.ae2_batchcraft.enabled" : "gui.ae2_batchcraft.disabled")));
    }

    private void drawSlotBackgrounds(GuiGraphics graphics, int offsetX, int offsetY, SlotSemantic semantic) {
        for (Slot slot : menu.getSlots(semantic)) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(graphics);
        }
    }

    private List<Component> getCompatibleUpgrades() {
        return List.of(
                GuiText.CompatibleUpgrades.text(),
                AEItems.INVERTER_CARD.stack().getHoverName());
    }
}
