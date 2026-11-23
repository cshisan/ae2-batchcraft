package org.ae2bc.part.unit.port.base;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import lombok.Getter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

@Getter
public class Pp2mpUnitIoTypePortMenu extends AEBaseMenu {
    private final Pp2mpUnitIoTypePortPart part;

    public Pp2mpUnitIoTypePortMenu(MenuType<?> menuType, int id, Inventory playerInventory, Pp2mpUnitIoTypePortPart part) {
        super(menuType, id, playerInventory, part);
        this.part = part;

        // 材料标记槽
        InternalInventory markers = this.part.getFilterMarkers().createMenuWrapper();
        for (int i = 0; i < markers.size(); i++) {
            this.addSlot(new FakeSlot(markers, i), SlotSemantics.CONFIG);
        }
        // 升级卡槽。允许的升级类型由 AE2 的 Upgrades 注册表负责。
        this.addSlot(new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.UPGRADES,
                this.part.getUpgrades(), 0
        ), SlotSemantics.UPGRADE);
        // 玩家物品槽
        this.createPlayerInventorySlots(playerInventory);
    }
}
