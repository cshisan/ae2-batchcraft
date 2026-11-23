package org.ae2bc.client.screen;

import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.GuiText;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.menu.implementations.PriorityMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.client.screen.component.ScaledIconButton;
import org.ae2bc.client.screen.component.TextSwitch;
import org.ae2bc.client.screen.component.OutputPortModeButton;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortMenu;

import java.util.Locale;

public class Pp2mpUnitOutputTypeScreen extends Pp2mpFilterScreen<Pp2mpUnitOutputTypePortMenu> {
    private final OutputPortModeButton[] modeButtons = new OutputPortModeButton[4];
    private TextSwitch syncSwitch;

    public Pp2mpUnitOutputTypeScreen(Pp2mpUnitOutputTypePortMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, Component.translatable(
                "gui.ae2_batchcraft.unit_port_output_type.title",
                Component.translatable("gui.ae2_batchcraft.unit_port."
                        + menu.getPart().getType().name().toLowerCase(Locale.ROOT))
        ), style);
        this.setTextContent("dialog_title", this.title);

        // 同步开关
        this.syncSwitch = new TextSwitch(
                style, () -> this.menu.switchChange(this.syncSwitch.isSelected()),
                Component.translatable("gui.ae2_batchcraft.sync_switch")
        );
        this.widgets.add("sync", this.syncSwitch);
        this.updateSyncSwitch();

        ScaledIconButton priorityButton = this.addToLeftToolbar(new ScaledIconButton(
                ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "textures/gui/btn/priority.png"),
                0.8F, button -> PacketDistributor.sendToServer(SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE))));
        priorityButton.setMessage(GuiText.Priority.text());

        for (OutputPortMode mode : OutputPortMode.values()) {
            OutputPortModeButton button = new OutputPortModeButton(mode, this.menu::setOutputPortMode);
            this.widgets.add(button.widgetId(), button);
            this.modeButtons[mode.id] = button;
        }
        this.updateModeButtons();

    }

    @Override
    protected void init() {
        super.init();

        int syncWidth = this.syncSwitch.fitToMessage();
        this.syncSwitch.setX(this.leftPos + this.imageWidth - 16 - syncWidth);

        this.updateSyncSwitch();
        this.updateModeButtons();
    }

    private void updateSyncSwitch() {
        boolean synced = this.menu.isSyncSwitch();
        this.syncSwitch.visible = true;
        this.syncSwitch.active = true;
        this.syncSwitch.setSelected(synced);
    }

    private void updateModeButtons() {
        for (OutputPortModeButton button : this.modeButtons) {
            button.update(this.menu.getOutputPortMode(), this.menu.isSyncSwitch());
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.updateSyncSwitch();
        this.updateModeButtons();
    }

}
