package cn.ae2bc.logic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import appeng.api.parts.IPart;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.InterfacePart;
import appeng.tile.networking.CableBusTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Uses AE2's own interface tick to drain returned items without bypassing its state handling. */
public final class InterfaceReturnFlusher {
    private static final Set<DualityInterface> FLUSHING = Collections.newSetFromMap(
            new IdentityHashMap<DualityInterface, Boolean>());

    private InterfaceReturnFlusher() { }

    public static ItemStack insert(TileEntity tile, Direction targetSide, IItemHandler handler,
            ItemStack stack, boolean simulate) {
        if (simulate) {
            return ItemHandlerHelper.insertItem(handler, stack, true);
        }
        DualityInterface duality = findDuality(tile, targetSide);
        if (duality != null && !isEmpty(duality.getStorage())) {
            flush(duality);
        }
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, false);
        if (duality != null && remainder.getCount() < stack.getCount()) {
            flush(duality);
        }
        return remainder;
    }

    private static DualityInterface findDuality(TileEntity tile, Direction targetSide) {
        if (tile instanceof IInterfaceHost) {
            return ((IInterfaceHost) tile).getInterfaceDuality();
        }
        if (tile instanceof CableBusTileEntity) {
            IPart part = ((CableBusTileEntity) tile).getPart(targetSide);
            if (part instanceof InterfacePart) {
                return ((InterfacePart) part).getInterfaceDuality();
            }
        }
        return null;
    }

    private static boolean isEmpty(IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void flush(DualityInterface duality) {
        if (!FLUSHING.add(duality)) {
            return;
        }
        try {
            duality.tickingRequest(null, 1);
        } finally {
            FLUSHING.remove(duality);
        }
    }
}
