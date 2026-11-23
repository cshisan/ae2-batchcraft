package org.ae2bc.part.unit.port.base;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.parts.AEBasePart;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.Pp2mpUnitPortKeys;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;

@Getter
public class Pp2mpUnitOutputTypePortMenu extends Pp2mpUnitIoTypePortMenu {
    public static final MenuType<Pp2mpUnitOutputTypePortMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpUnitOutputTypePortMenu::new, Pp2mpUnitOutputTypePortPart.class)
            .withMenuTitle(AEBasePart::getName)
            .build(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_unit_output_type_port"));

    private final Pp2mpUnitOutputTypePortPart part;

    public Pp2mpUnitOutputTypePortMenu(MenuType<?> menuType, int id, Inventory playerInventory, Pp2mpUnitOutputTypePortPart part) {
        super(menuType, id, playerInventory, part);
        this.part = part;

        this.registerClientAction(Pp2mpUnitPortKeys.OUTPUT_PORT_MODE, Integer.class, value -> {
            if (this.isServerSide() && value != null && !this.part.getData().isSyncEnabled()
                    && value >= 0 && value <= OutputPortMode.SAME_TYPE.id) {
                this.part.getData().update(() -> this.part.getData().setOutputPortMode(OutputPortMode.null2defaultOf(value)));
                this.part.wakeManager();
            }
        });
        this.registerClientAction(Pp2mpUnitPortKeys.UNIT_PORT_SYNCED_ENABLED, Boolean.class, value -> {
            if (this.isServerSide() && value != null) {
                this.part.getData().update(() -> this.part.getData().setSyncEnabled(value));
                if (value) {
                    Pp2mpUnitManagerPart manager = this.part.findManager();
                    if (manager != null) {
                        this.part.getData().apply(manager.getData().getConfig());
                    }
                }
                this.part.wakeManager();
            }
        });
    }

    public boolean isSyncSwitch() {
        return this.part.getData().isSyncEnabled();
    }

    public void switchChange(boolean synced) {
        this.sendClientAction(Pp2mpUnitPortKeys.UNIT_PORT_SYNCED_ENABLED, synced);
    }

    public OutputPortMode getOutputPortMode() {
        return this.part.getData().getOutputPortMode();
    }

    public void setOutputPortMode(OutputPortMode mode) {
        this.sendClientAction(Pp2mpUnitPortKeys.OUTPUT_PORT_MODE, mode.id);
    }

}
