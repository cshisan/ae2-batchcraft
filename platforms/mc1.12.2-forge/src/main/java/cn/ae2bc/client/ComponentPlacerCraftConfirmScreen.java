package cn.ae2bc.client;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiCraftConfirm;
import cn.ae2bc.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.IOException;
import java.lang.reflect.Field;

/** Keeps AE2's confirmation screen while returning Cancel to the component placer. */
public final class ComponentPlacerCraftConfirmScreen extends GuiCraftConfirm {
    private static final Field CANCEL_BUTTON = ReflectionHelper.findField(
            GuiCraftConfirm.class, "cancel");

    public ComponentPlacerCraftConfirmScreen(InventoryPlayer inventory, ITerminalHost host) {
        super(inventory, host);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == cancelButton()) {
            ModNetwork.sendComponentPlacerCraftReturn(inventorySlots.windowId);
            return;
        }
        super.actionPerformed(button);
    }

    private GuiButton cancelButton() {
        try {
            return (GuiButton) CANCEL_BUTTON.get(this);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access AE2 crafting cancel button", exception);
        }
    }
}
