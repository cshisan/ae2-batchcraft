package org.ae2bc.storage;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.FakeSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MarkerSlot extends FakeSlot {
    private final Component tooltip;

    public MarkerSlot(InternalInventory inventory, Component tooltip) {
        super(inventory, 0);
        this.tooltip = tooltip;
        this.setEmptyTooltip(() -> List.of(this.tooltip));
    }

    @Override
    public void set(ItemStack stack) {
        super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    @Override
    public void increase(ItemStack stack) {
        this.set(stack);
    }

    @Override
    public void decrease(ItemStack stack) {
        this.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
    }
}