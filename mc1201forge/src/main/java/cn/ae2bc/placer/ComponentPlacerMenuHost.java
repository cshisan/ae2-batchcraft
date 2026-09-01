package cn.ae2bc.placer;

import java.util.function.BiConsumer;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import appeng.util.inv.SupplierInternalInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ComponentPlacerMenuHost extends WirelessTerminalMenuHost {
    private final SupplierInternalInventory materials;
    private final SupplierInternalInventory cableFilter;
    private final SupplierInternalInventory partFilter;

    public ComponentPlacerMenuHost(ComponentPlacerItem item, Player player, int inventorySlot,
                                   ItemStack stack, BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        materials = new SupplierInternalInventory(() -> ComponentPlacerItem.getMaterialInventory(getItemStack()));
        cableFilter = new SupplierInternalInventory(() -> ComponentPlacerItem.getCableFilterInventory(getItemStack()));
        partFilter = new SupplierInternalInventory(() -> ComponentPlacerItem.getPartFilterInventory(getItemStack()));
    }

    public InternalInventory getMaterials() { return materials; }
    public InternalInventory getCableFilter() { return cableFilter; }
    public InternalInventory getPartFilter() { return partFilter; }
    public LinkStatus getLinkStatus() {
        return new LinkStatus(getActionableNode() != null && getInventory() != null);
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        if (!getLinkStatus().connected()) {
            // Keep the local-only GUI open while the wireless network is unavailable.
            return drainPower();
        }
        return super.onBroadcastChanges(menu);
    }

    public record LinkStatus(boolean connected) {
    }
}
