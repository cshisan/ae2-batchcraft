package cn.ae2bc.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Material return filter menu for input-type unit ports. */
public final class UnitPortInputConfigMenu extends AEBaseMenu implements ISubMenu {
    public static final MenuType<UnitPortInputConfigMenu> TYPE = MenuTypeBuilder
            .create(UnitPortInputConfigMenu::new, PatternP2PUnitPortPart.class)
            .withMenuTitle(part -> part.getMainMenuIcon().getHoverName())
            .build("unit_port_input_config");

    private final PatternP2PUnitPortPart host;

    public UnitPortInputConfigMenu(int id, Inventory playerInventory, PatternP2PUnitPortPart host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        if (!host.getType().returnsTaskOutput()) {
            throw new IllegalStateException(
                    "Input configuration menu opened for non-input unit port: " + host.getType());
        }
        InternalInventory markers = host.getInputFilterMarkers().createMenuWrapper();
        for (int i = 0; i < markers.size(); i++) {
            addSlot(new FakeSlot(markers, i), SlotSemantics.CONFIG);
        }
        addSlot(new InverterSlot(host.getInputFilterInverter(), 0), SlotSemantics.UPGRADE);
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public ISubMenuHost getHost() {
        return host;
    }

    private static final class InverterSlot extends RestrictedInputSlot {
        private InverterSlot(InternalInventory inventory, int slot) {
            super(PlacableItemType.UPGRADES, inventory, slot);
        }
    }
}
