package org.ae2bc.ioport;

import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.ItemTransfer;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.ae2bc.core.ioport.CellTransferMode;

import java.util.function.UnaryOperator;

public final class IoPortTransfer {
    private static final RelativeSide[] SIDES = RelativeSide.values();

    private IoPortTransfer() {
    }

    /* 仅转移原生逻辑判定已完成的槽位，按实际接收量扣除并保留剩余。 */
    public static boolean move(InternalInventory input, int slot, ItemTransfer output,
                               CellTransferMode mode, UnaryOperator<ItemStack> external) {
        ItemStack stack = input.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack remainder = IoPortTransfer.route(mode, stack, output::addItems, external);
        if (remainder.getCount() != stack.getCount()) {
            input.setItemDirect(slot, remainder);
        }
        return remainder.isEmpty();
    }

    private static ItemStack route(CellTransferMode mode, ItemStack stack,
                                   UnaryOperator<ItemStack> internal, UnaryOperator<ItemStack> external) {
        return switch (mode) {
            case SLOTS -> internal.apply(stack);
            case EXTERNAL -> external.apply(stack);
            case PREFER_SLOTS -> IoPortTransfer.fallback(stack, internal, external);
            case PREFER_EXTERNAL -> IoPortTransfer.fallback(stack, external, internal);
        };
    }

    private static ItemStack fallback(ItemStack stack, UnaryOperator<ItemStack> first,
                                      UnaryOperator<ItemStack> second) {
        ItemStack remainder = first.apply(stack);
        return remainder.isEmpty() ? ItemStack.EMPTY : second.apply(remainder);
    }

    /* 只访问已加载的选中邻面；按真实剩余量尝试下一面，不预先搬入缓存。 */
    public static ItemStack insertExternal(Level level, BlockPos pos, BlockOrientation orientation,
                                           IoPortTransferData data, ItemStack stack) {
        ItemStack remainder = stack;
        for (RelativeSide side : SIDES) {
            if (!data.enabled(side)) {
                continue;
            }
            Direction direction = orientation.getSide(side);
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target)) {
                continue;
            }
            ItemTransfer inventory = InternalInventory.wrapExternal(level, target, direction.getOpposite());
            if (inventory != null) {
                remainder = inventory.addItems(remainder);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
        return remainder;
    }
}
