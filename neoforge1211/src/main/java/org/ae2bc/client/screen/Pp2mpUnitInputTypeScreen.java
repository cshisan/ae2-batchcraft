package org.ae2bc.client.screen;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortMenu;

import java.util.Locale;

public class Pp2mpUnitInputTypeScreen extends Pp2mpFilterScreen<Pp2mpUnitInputTypePortMenu> {

    public Pp2mpUnitInputTypeScreen(Pp2mpUnitInputTypePortMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, Component.translatable(
                "gui.ae2_batchcraft.unit_port_input_type.title",
                Component.translatable("gui.ae2_batchcraft.unit_port." + menu.getPart().getType().name().toLowerCase(Locale.ROOT))
        ), style);
        this.setTextContent("dialog_title", this.title);
    }
}
