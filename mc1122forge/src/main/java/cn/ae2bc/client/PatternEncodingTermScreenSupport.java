package cn.ae2bc.client;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotFakeCraftingMatrix;
import cn.ae2bc.logic.DirectionLayout;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;

/** Client bridge called by the narrowly-scoped GuiPatternTerm ASM overrides. */
public final class PatternEncodingTermScreenSupport {
    private PatternEncodingTermScreenSupport() { }

    public static boolean handleMouseClicked(GuiPatternTerm screen, Slot slot, int mouseButton) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!GuiScreen.isCtrlKeyDown()
                || !minecraft.gameSettings.keyBindPickBlock.isActiveAndMatches(mouseButton - 100)
                || minecraft.player == null
                || !(minecraft.player.openContainer instanceof ContainerPatternTerm)) return false;
        ContainerPatternTerm container = (ContainerPatternTerm) minecraft.player.openContainer;
        int inputSlot = getInputSlot(container, slot);
        if (container.isCraftingMode() || inputSlot < 0 || !slot.getHasStack()) return false;

        ContainerSubScreen.switchTo(new MaterialOutputConfigScreen(screen, container.windowId, inputSlot,
                slot.getStack(), DirectionLayout.fromPlayerFacing(
                        minecraft.player.getHorizontalFacing())));
        return true;
    }

    public static void renderConfigMarker(GuiPatternTerm screen, Slot slot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || !(minecraft.player.openContainer instanceof ContainerPatternTerm)) return;
        ContainerPatternTerm container = (ContainerPatternTerm) minecraft.player.openContainer;
        int inputSlot = getInputSlot(container, slot);
        if (inputSlot < 0) return;
        MaterialOutputConfigData config = MaterialOutputClientState.get(container.windowId);
        if (config.getDirection(inputSlot) == null
                && config.getOutputForm(inputSlot) == MaterialOutputForm.NORMAL) return;
        Gui.drawRect(slot.xPos + 15, slot.yPos, slot.xPos + 16, slot.yPos + 3, 0xFF2FCCB7);
        Gui.drawRect(slot.xPos + 13, slot.yPos, slot.xPos + 16, slot.yPos + 1, 0xFF65E8C9);
    }

    public static void renderInputTooltip(GuiPatternTerm screen, Slot slot, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || !(minecraft.player.openContainer instanceof ContainerPatternTerm)) return;
        ContainerPatternTerm container = (ContainerPatternTerm) minecraft.player.openContainer;
        if (getInputSlot(container, slot) < 0 || slot == null || !slot.getHasStack()) return;

        ITooltipFlag flag = minecraft.gameSettings.advancedItemTooltips
                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        List<String> tooltip = new ArrayList<String>(slot.getStack().getTooltip(minecraft.player, flag));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2_batchcraft.material_output_config"));
        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screen.width, screen.height,
                200, minecraft.fontRenderer);
    }

    private static int getInputSlot(ContainerPatternTerm container, Slot slot) {
        if (slot == null || container.isCraftingMode() || !(slot instanceof SlotFakeCraftingMatrix)) return -1;
        int inputSlot = 0;
        for (Slot candidate : container.inventorySlots) {
            if (!(candidate instanceof SlotFakeCraftingMatrix)) continue;
            if (candidate == slot) return inputSlot;
            inputSlot++;
        }
        return -1;
    }
}
