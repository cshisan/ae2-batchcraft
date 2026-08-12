package cn.ae2bc.client;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.gui.widgets.GuiTabButton;
import cn.ae2bc.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.IOException;
import java.lang.reflect.Field;

/** Reuses AE2's amount screen while retaining the component placer's hand context. */
public final class ComponentPlacerCraftAmountScreen extends GuiCraftAmount {
    private static final Field AMOUNT_TO_CRAFT = ReflectionHelper.findField(
            GuiCraftAmount.class, "amountToCraft");
    private static final Field NEXT_BUTTON = ReflectionHelper.findField(
            GuiCraftAmount.class, "next");
    private static final Field ORIGINAL_GUI_BUTTON = ReflectionHelper.findField(
            GuiCraftAmount.class, "originalGuiBtn");

    private final int initialAmount;
    private final ItemStack placer;
    private GuiButton returnButton;

    public ComponentPlacerCraftAmountScreen(InventoryPlayer inventory, ITerminalHost host,
                                            ItemStack placer, int initialAmount) {
        super(inventory, host);
        this.placer = placer;
        this.initialAmount = Math.max(1, initialAmount);
    }

    @Override
    public void initGui() {
        super.initGui();
        amountField().setText(Integer.toString(initialAmount));
        GuiButton original = originalGuiButton();
        if (original != null) buttonList.remove(original);
        returnButton = new GuiTabButton(guiLeft + 154, guiTop, placer,
                placer.getDisplayName(), itemRender);
        buttonList.add(returnButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == returnButton) {
            ModNetwork.sendComponentPlacerCraftReturn(inventorySlots.windowId);
            return;
        }
        if (button == nextButton()) {
            try {
                int amount = Integer.parseInt(amountField().getText());
                if (amount > 0) {
                    ModNetwork.sendComponentPlacerCraftRequest(
                            inventorySlots.windowId, amount, isShiftKeyDown());
                }
            } catch (NumberFormatException ignored) {
                amountField().setText("1");
            }
            return;
        }
        super.actionPerformed(button);
    }

    private GuiNumberBox amountField() {
        try {
            return (GuiNumberBox) AMOUNT_TO_CRAFT.get(this);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access AE2 crafting amount field", exception);
        }
    }

    private GuiButton nextButton() {
        try {
            return (GuiButton) NEXT_BUTTON.get(this);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access AE2 crafting next button", exception);
        }
    }

    private GuiButton originalGuiButton() {
        try {
            return (GuiButton) ORIGINAL_GUI_BUTTON.get(this);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access AE2 crafting return button", exception);
        }
    }
}
