package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.gui.components.Button;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.menu.ProductExtractionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ProductExtractionScreen extends AEBaseScreen<ProductExtractionMenu> {
    private ValidatedIntegerField intervalInput;
    private ValidatedIntegerField amountInput;
    private final Ae2StyleToggleSwitch modeToggle;
    private final Button intervalReset;
    private final Button amountReset;

    public ProductExtractionScreen(ProductExtractionMenu menu, Inventory playerInventory,
                                   Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        AESubScreen.addBackButton(menu, "back", widgets);
        modeToggle = new Ae2StyleToggleSwitch(Component.empty());
        modeToggle.setChangeListener(this::changeFilterMode);
        widgets.add("modeToggle", modeToggle);
        modeToggle.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.toggle")));
        intervalReset = widgets.addButton("intervalReset",
                Component.translatable("gui.ae2_batchcraft.product_extraction.reset"),
                () -> menu.setInterval(ProductExtractionSettings.DEFAULT_INTERVAL));
        intervalReset.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.interval_reset.tooltip")));
        amountReset = widgets.addButton("amountReset",
                Component.translatable("gui.ae2_batchcraft.product_extraction.reset"),
                () -> menu.setAmount(ProductExtractionSettings.DEFAULT_AMOUNT));
        amountReset.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.amount_reset.tooltip")));
    }

    @Override
    protected void init() {
        super.init();
        intervalInput = addRenderableWidget(new ValidatedIntegerField(font,
                leftPos + 58, topPos + 21, 40, 16,
                Component.translatable("gui.ae2_batchcraft.product_extraction.interval"),
                () -> ProductExtractionSettings.MIN_INTERVAL,
                () -> ProductExtractionSettings.MAX_INTERVAL, menu::setInterval));
        amountInput = addRenderableWidget(new ValidatedIntegerField(font,
                leftPos + 58, topPos + 42, 40, 16,
                Component.translatable("gui.ae2_batchcraft.product_extraction.amount"),
                () -> ProductExtractionSettings.MIN_AMOUNT,
                () -> ProductExtractionSettings.MAX_AMOUNT, menu::setAmount));
        syncInputs();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, ProductExtractionMenu.MARKER_SLOT);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, SlotSemantics.PLAYER_INVENTORY);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, SlotSemantics.PLAYER_HOTBAR);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        guiGraphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        syncInputs();
        modeToggle.active = menu.cardInstalled;
        modeToggle.setSelected(menu.whitelist);
        intervalReset.active = menu.cardInstalled
                && menu.interval != ProductExtractionSettings.DEFAULT_INTERVAL;
        amountReset.active = menu.cardInstalled
                && menu.amount != ProductExtractionSettings.DEFAULT_AMOUNT;
    }

    private void syncInputs() {
        if (intervalInput == null || amountInput == null) {
            return;
        }
        intervalInput.syncValue(menu.interval);
        amountInput.syncValue(menu.amount);
        boolean active = menu.cardInstalled;
        intervalInput.setEditable(active);
        amountInput.setEditable(active);
    }

    private void changeFilterMode() {
        if (menu.cardInstalled) {
            menu.setWhitelist(modeToggle.isSelected());
        }
    }

    private void drawSlotBackgrounds(GuiGraphics guiGraphics, int offsetX, int offsetY, SlotSemantic semantic) {
        for (var slot : menu.getSlots(semantic)) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(guiGraphics);
        }
    }

}
