package cn.ae2bc.client;

import appeng.client.gui.me.items.PatternTermScreen;
import appeng.container.me.items.PatternTermContainer;
import appeng.container.slot.FakeCraftingMatrixSlot;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.logic.DirectionLayout;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.inventory.container.Slot;

/** Shared processing-input lookup and sub-screen opening for the pattern terminal. */
public final class PatternEncodingTermScreenSupport {
    private PatternEncodingTermScreenSupport() { }

    public static boolean handleMouseClicked(PatternTermScreen screen, Slot slot, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Screen.hasControlDown() || !minecraft.options.keyPickItem.matchesMouse(button)
                || slot == null || !slot.hasItem()) {
            return false;
        }
        PatternTermContainer container = (PatternTermContainer) screen.getMenu();
        int inputSlot = getProcessingInputSlotIndex(container, slot);
        if (inputSlot < 0) {
            return false;
        }
        MaterialOutputConfigData config = ((PatternEncodingTermMenuExtension) container)
                .ae2bc$getMaterialOutputConfig();
        ContainerSubScreen.switchTo(new MaterialOutputConfigScreen(screen, container.containerId,
                inputSlot, slot.getItem(), config, DirectionLayout.fromPlayerFacing(
                        minecraft.player == null ? null : minecraft.player.getDirection())));
        return true;
    }

    public static int getProcessingInputSlotIndex(PatternTermContainer container, Slot slot) {
        if (slot == null || container.isCraftingMode()) {
            return -1;
        }
        FakeCraftingMatrixSlot[] inputSlots = container.getCraftingGridSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            if (inputSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
