package cn.ae2bc.logic;

import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.world.item.ItemStack;

/** Persistent item marker inventory used by output unit-port filtering. */
public final class UnitPortFilterInventory extends AppEngInternalInventory {
    public UnitPortFilterInventory(int size) {
        super(size);
        for (int i = 0; i < size; i++) setMaxStackSize(i, 1);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < size() && !stack.isEmpty();
    }
}
