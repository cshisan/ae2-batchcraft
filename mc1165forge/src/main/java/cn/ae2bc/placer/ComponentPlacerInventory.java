package cn.ae2bc.placer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.items.ItemStackHandler;

final class ComponentPlacerInventory extends ItemStackHandler {
    interface Filter {
        boolean allow(ComponentPlacerInventory inventory, int slot, ItemStack stack);
    }

    private final ItemStack owner;
    private final String key;
    private final int stackLimit;
    private final Filter filter;
    private boolean loading;

    ComponentPlacerInventory(ItemStack owner, String key, int size, int stackLimit, Filter filter) {
        this(owner, key, size, stackLimit, filter, false);
    }

    ComponentPlacerInventory(ItemStack owner, String key, int size, int stackLimit, Filter filter,
                             boolean fixedSerializedSize) {
        super(size);
        this.owner = owner;
        this.key = key;
        this.stackLimit = stackLimit;
        this.filter = filter;
        CompoundNBT root = owner.getTag();
        if (root != null && root.contains(key)) {
            loading = true;
            deserializeNBT(root.getCompound(key));
            if (fixedSerializedSize && getSlots() != size) {
                ItemStack[] retained = new ItemStack[size];
                for (int slot = 0; slot < size; slot++) {
                    retained[slot] = slot < getSlots() ? getStackInSlot(slot).copy() : ItemStack.EMPTY;
                }
                setSize(size);
                for (int slot = 0; slot < size; slot++) {
                    setStackInSlot(slot, retained[slot]);
                }
            }
            loading = false;
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return filter == null || filter.allow(this, slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return stackLimit;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!loading) owner.getOrCreateTag().put(key, serializeNBT());
    }
}
