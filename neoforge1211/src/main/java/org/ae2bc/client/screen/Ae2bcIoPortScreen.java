package org.ae2bc.client.screen;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.SlotSemantic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.client.screen.component.ScaledIconButton;
import org.ae2bc.core.ioport.CellTransferMode;
import org.ae2bc.ioport.Ae2bcIoPortMenu;

public final class Ae2bcIoPortScreen extends UpgradeableScreen<Ae2bcIoPortMenu> {
    private static final SlotSemantic[] SLOT_GROUPS = {SlotSemantics.MACHINE_INPUT, SlotSemantics.MACHINE_OUTPUT,
            SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR};
    private final SettingToggleButton<FullnessMode> fullMode;
    private final SettingToggleButton<OperationMode> operationMode;
    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final ScaledIconButton transfer;
    private final ScaledIconButton sides;

    public Ae2bcIoPortScreen(Ae2bcIoPortMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        ScreenHeader.addTo(this.widgets);
        this.fullMode = this.addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FULLNESS_MODE, FullnessMode.EMPTY));
        this.redstoneMode = this.addToLeftToolbar(
                new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE));
        this.operationMode = new ServerSettingToggleButton<>(Settings.OPERATION_MODE, OperationMode.EMPTY);
        this.widgets.add("operationMode", this.operationMode);
        this.transfer = this.addToLeftToolbar(new ScaledIconButton(
                Icon.ARROW_RIGHT, 1.0F, button -> this.menu.cycleTransferMode()));
        this.sides = this.addToLeftToolbar(new ScaledIconButton(
                ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "textures/gui/btn/output_sides.png"),
                1.0F, button -> this.switchToScreen(new IoPortOutputSidesScreen(this))));
        this.sides.setMessage(Component.translatable("gui.ae2_batchcraft.io_port.output_sides")
                .append("\n").append(Component.translatable("gui.ae2_batchcraft.io_port.output_sides.hint")));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.fullMode.set(this.menu.getFullMode());
        this.operationMode.set(this.menu.getOperationMode());
        this.redstoneMode.set(this.menu.getRedStoneMode());
        this.redstoneMode.setVisibility(this.menu.hasUpgrade(AEItems.REDSTONE_CARD));
        CellTransferMode mode = this.menu.getTransferData().mode();
        this.transfer.setAe2Icon(switch (mode) {
            case SLOTS -> Icon.ARROW_RIGHT;
            case EXTERNAL -> Icon.ARROW_LEFT;
            case PREFER_SLOTS, PREFER_EXTERNAL -> Icon.AUTO_EXPORT_ON;
        });
        this.transfer.setMirrored(mode == CellTransferMode.PREFER_EXTERNAL);
        this.transfer.setMessage(Component.translatable("gui.ae2_batchcraft.io_port.transfer")
                .append("\n").append(Component.translatable("gui.ae2_batchcraft.io_port.mode." + mode.id()))
                .append("\n").append(Component.translatable("gui.ae2_batchcraft.io_port.mode." + mode.id() + ".hint")));
        this.sides.setVisibility(mode == CellTransferMode.EXTERNAL || mode == CellTransferMode.PREFER_EXTERNAL);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTick);
        for (SlotSemantic semantics : SLOT_GROUPS) {
            SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(semantics));
        }
        this.drawItem(graphics, offsetX + 58, offsetY + 27, AEItems.ITEM_CELL_1K.stack());
        this.drawItem(graphics, offsetX + 102, offsetY + 27, AEBlocks.DRIVE.stack());
    }
}
