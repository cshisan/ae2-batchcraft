package org.ae2bc.part.unit.port.base;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.parts.AEBasePart;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.ae2bc.Ae2bcMod;

@Getter
public class Pp2mpUnitInputTypePortMenu extends Pp2mpUnitIoTypePortMenu {
    public static final MenuType<Pp2mpUnitInputTypePortMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpUnitInputTypePortMenu::new, Pp2mpUnitInputTypePortPart.class)
            .withMenuTitle(AEBasePart::getName)
            .build(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_unit_input_type_port"));

    private final Pp2mpUnitInputTypePortPart part;

    public Pp2mpUnitInputTypePortMenu(MenuType<?> menuType, int id, Inventory playerInventory, Pp2mpUnitInputTypePortPart part) {
        super(menuType, id, playerInventory, part);
        this.part = part;
    }
}
