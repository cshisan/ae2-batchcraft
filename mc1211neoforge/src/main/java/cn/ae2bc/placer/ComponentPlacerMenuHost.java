package cn.ae2bc.placer;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.SupplierInternalInventory;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

public final class ComponentPlacerMenuHost
        extends WirelessTerminalMenuHost<ComponentPlacerItem> {
    private final SupplierInternalInventory<InternalInventory> materials;
    private final SupplierInternalInventory<InternalInventory> cableFilter;
    private final SupplierInternalInventory<InternalInventory> partFilter;

    public ComponentPlacerMenuHost(ComponentPlacerItem item, Player player,
                             ItemMenuHostLocator locator,
                             BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        materials = new SupplierInternalInventory<>(new StackDependentSupplier<>(
                this::getItemStack, ComponentPlacerItem::getMaterialInventory));
        cableFilter = new SupplierInternalInventory<>(new StackDependentSupplier<>(
                this::getItemStack, ComponentPlacerItem::getCableFilterInventory));
        partFilter = new SupplierInternalInventory<>(new StackDependentSupplier<>(
                this::getItemStack, ComponentPlacerItem::getPartFilterInventory));
    }

    public InternalInventory getMaterials() {
        return materials;
    }

    public InternalInventory getCableFilter() {
        return cableFilter;
    }

    public InternalInventory getPartFilter() {
        return partFilter;
    }

}
