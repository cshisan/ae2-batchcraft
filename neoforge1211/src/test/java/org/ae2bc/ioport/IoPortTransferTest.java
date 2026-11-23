package org.ae2bc.ioport;

import appeng.api.inventories.ItemTransfer;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.ae2bc.core.ioport.CellTransferMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Objects;

class IoPortTransferTest {
    private static AppEngInternalInventory inventory(int count, int capacity) {
        AppEngInternalInventory inventory = new AppEngInternalInventory(1);
        inventory.setMaxStackSize(0, capacity);
        if (count > 0) {
            inventory.setItemDirect(0, new ItemStack(Items.STONE, count));
        }
        return inventory;
    }

    /* 枚举四种模式及各类部分容量，验证优先顺序与物品总量守恒。 */
    @Test
    void allModesConserveItemsAndRespectPriorityAcrossPartialCapacities() {
        for (CellTransferMode mode : CellTransferMode.values()) {
            for (int internalCapacity : new int[]{0, 1, 5, 12}) {
                for (int externalCapacity : new int[]{0, 1, 5, 12}) {
                    AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
                    AppEngInternalInventory output = IoPortTransferTest.inventory(0, internalCapacity);
                    AppEngInternalInventory external = IoPortTransferTest.inventory(0, externalCapacity);
                    boolean complete = IoPortTransfer.move(input, 0, output, mode, external::addItems);
                    int expectedInternal;
                    int expectedExternal;
                    switch (mode) {
                        case SLOTS -> {
                            expectedInternal = Math.min(8, internalCapacity);
                            expectedExternal = 0;
                        }
                        case EXTERNAL -> {
                            expectedInternal = 0;
                            expectedExternal = Math.min(8, externalCapacity);
                        }
                        case PREFER_SLOTS -> {
                            expectedInternal = Math.min(8, internalCapacity);
                            expectedExternal = Math.min(8 - expectedInternal, externalCapacity);
                        }
                        case PREFER_EXTERNAL -> {
                            expectedExternal = Math.min(8, externalCapacity);
                            expectedInternal = Math.min(8 - expectedExternal, internalCapacity);
                        }
                        default -> throw new AssertionError(mode);
                    }
                    Assertions.assertEquals(expectedInternal, output.getStackInSlot(0).getCount(), mode.name());
                    Assertions.assertEquals(expectedExternal, external.getStackInSlot(0).getCount(), mode.name());
                    Assertions.assertEquals(8 - expectedInternal - expectedExternal,
                            input.getStackInSlot(0).getCount(), mode.name());
                    Assertions.assertEquals(input.isEmpty(), complete, mode.name());
                }
            }
        }
    }

    @Test
    void exclusiveModesAndSuccessfulFirstChoiceNeverVisitOtherDestination() {
        ItemTransfer forbidden = Mockito.mock(ItemTransfer.class);
        for (CellTransferMode mode : new CellTransferMode[]{CellTransferMode.EXTERNAL, CellTransferMode.PREFER_EXTERNAL}) {
            AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
            AppEngInternalInventory external = IoPortTransferTest.inventory(0, 64);
            Assertions.assertTrue(IoPortTransfer.move(input, 0, forbidden, mode, external::addItems));
        }
        Mockito.verifyNoInteractions(forbidden);
        for (CellTransferMode mode : new CellTransferMode[]{CellTransferMode.SLOTS, CellTransferMode.PREFER_SLOTS}) {
            AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
            Assertions.assertTrue(IoPortTransfer.move(input, 0, IoPortTransferTest.inventory(0, 64), mode,
                    stack -> Assertions.fail("不应访问外部库存")));
        }
    }

    @Test
    void blockedRemainderCanRetryWithoutMovingOldOutputStacks() {
        AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
        AppEngInternalInventory output = IoPortTransferTest.inventory(4, 4);
        AppEngInternalInventory external = IoPortTransferTest.inventory(0, 3);
        Assertions.assertFalse(IoPortTransfer.move(input, 0, output, CellTransferMode.PREFER_SLOTS, external::addItems));
        Assertions.assertEquals(5, input.getStackInSlot(0).getCount());
        Assertions.assertFalse(IoPortTransfer.move(input, 0, output, CellTransferMode.PREFER_SLOTS, external::addItems));
        Assertions.assertEquals(5, input.getStackInSlot(0).getCount());
        external.setMaxStackSize(0, 8);
        Assertions.assertTrue(IoPortTransfer.move(input, 0, output, CellTransferMode.PREFER_SLOTS, external::addItems));
        Assertions.assertEquals(8, external.getStackInSlot(0).getCount());
        Assertions.assertEquals(4, output.getStackInSlot(0).getCount());
    }

    @Test
    void noSelectedSideWaitsOrFallsBackWithoutLookingUpCapabilities() {
        Level level = Mockito.mock(Level.class);
        for (CellTransferMode mode : CellTransferMode.values()) {
            AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
            AppEngInternalInventory output = IoPortTransferTest.inventory(0, 64);
            IoPortTransferData data = new IoPortTransferData(mode, 0);
            boolean complete = IoPortTransfer.move(input, 0, output, mode,
                    stack -> IoPortTransfer.insertExternal(level, BlockPos.ZERO,
                            BlockOrientation.get(Direction.NORTH), data, stack));
            Assertions.assertEquals(mode != CellTransferMode.EXTERNAL, complete);
        }
        Mockito.verifyNoInteractions(level);
    }

    @Test
    void externalInsertionUsesOppositeContactFaceAndFollowsBlockOrientation() {
        Level level = Mockito.mock(Level.class);
        AppEngInternalInventory first = IoPortTransferTest.inventory(0, 3);
        AppEngInternalInventory second = IoPortTransferTest.inventory(0, 4);
        BlockPos pos = new BlockPos(12, 64, 12);
        BlockOrientation orientation = BlockOrientation.get(Direction.EAST);
        Direction front = orientation.getSide(RelativeSide.FRONT);
        Direction back = orientation.getSide(RelativeSide.BACK);
        Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
        Mockito.when(level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(front), front.getOpposite()))
                .thenReturn(first.toItemHandler());
        Mockito.when(level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(back), back.getOpposite()))
                .thenReturn(second.toItemHandler());
        IoPortTransferData data = new IoPortTransferData(CellTransferMode.EXTERNAL, 0)
                .toggle(RelativeSide.FRONT).toggle(RelativeSide.BACK);
        ItemStack remainder = IoPortTransfer.insertExternal(level, pos, orientation, data, new ItemStack(Items.STONE, 10));
        Assertions.assertEquals(3, remainder.getCount());
        Assertions.assertEquals(3, first.getStackInSlot(0).getCount());
        Assertions.assertEquals(4, second.getStackInSlot(0).getCount());
        Mockito.verify(level, Mockito.times(2)).getCapability(Mockito.eq(Capabilities.ItemHandler.BLOCK),
                Mockito.any(BlockPos.class), Mockito.any(Direction.class));
    }

    @Test
    void missingCapabilityAndUnloadedNeighborKeepTheStack() {
        Level level = Mockito.mock(Level.class);
        IoPortTransferData data = new IoPortTransferData(CellTransferMode.EXTERNAL, 0)
                .toggle(RelativeSide.FRONT);
        ItemStack stack = new ItemStack(Items.STONE, 8);
        Assertions.assertSame(stack, IoPortTransfer.insertExternal(level, BlockPos.ZERO,
                BlockOrientation.get(Direction.NORTH), data, stack));
        Mockito.verify(level, Mockito.never()).getCapability(Mockito.eq(Capabilities.ItemHandler.BLOCK),
                Mockito.any(BlockPos.class), Mockito.any(Direction.class));
        Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
        Assertions.assertSame(stack, IoPortTransfer.insertExternal(level, BlockPos.ZERO,
                BlockOrientation.get(Direction.NORTH), data, stack));
    }

    @Test
    void rejectedItemRemainsInInput() {
        Level level = Mockito.mock(Level.class);
        IItemHandler handler = Mockito.mock(IItemHandler.class);
        Mockito.when(handler.getSlots()).thenReturn(1);
        Mockito.when(handler.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        Mockito.when(handler.insertItem(Mockito.eq(0), Mockito.any(ItemStack.class), Mockito.anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1, ItemStack.class));
        Mockito.when(level.isLoaded(Mockito.any(BlockPos.class))).thenReturn(true);
        Mockito.when(level.getCapability(Capabilities.ItemHandler.BLOCK, BlockPos.ZERO.north(), Direction.SOUTH))
                .thenReturn(handler);
        AppEngInternalInventory input = IoPortTransferTest.inventory(8, 64);
        IoPortTransferData data = new IoPortTransferData(CellTransferMode.EXTERNAL, 0).toggle(RelativeSide.FRONT);
        Assertions.assertFalse(IoPortTransfer.move(input, 0, IoPortTransferTest.inventory(0, 64), data.mode(),
                stack -> IoPortTransfer.insertExternal(level, BlockPos.ZERO,
                        BlockOrientation.get(Direction.NORTH), data, stack)));
        Assertions.assertEquals(8, input.getStackInSlot(0).getCount());
    }

    @Test
    void emptyInputDoesNotTouchAnyTarget() {
        ItemTransfer output = Mockito.mock(ItemTransfer.class);
        Assertions.assertFalse(IoPortTransfer.move(IoPortTransferTest.inventory(0, 64), 0, output,
                CellTransferMode.PREFER_EXTERNAL, stack -> Assertions.fail("空槽不应执行转移")));
        Mockito.verifyNoInteractions(output);
    }

    @Test
    void storageCellKeepsAllComponents() {
        for (CellTransferMode mode : CellTransferMode.values()) {
            ItemStack original = AEItems.ITEM_CELL_1K.stack();
            original.set(DataComponents.CUSTOM_NAME, Component.literal("保留元件数据"));
            CompoundTag payload = new CompoundTag();
            payload.putLong("test_contents", 1048576);
            original.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
            StorageCell cell = Objects.requireNonNull(StorageCells.getCellInventory(original, null));
            Assertions.assertEquals(128, cell.insert(AEItemKey.of(Items.STONE), 128,
                    Actionable.MODULATE, IActionSource.empty()));
            cell.persist();
            AppEngInternalInventory input = new AppEngInternalInventory(1);
            AppEngInternalInventory output = new AppEngInternalInventory(1);
            AppEngInternalInventory external = new AppEngInternalInventory(1);
            input.setItemDirect(0, original);
            Assertions.assertTrue(IoPortTransfer.move(input, 0, output, mode, external::addItems));
            ItemStack result = output.isEmpty() ? external.getStackInSlot(0) : output.getStackInSlot(0);
            Assertions.assertTrue(ItemStack.matches(original, result));
            Assertions.assertTrue(input.isEmpty());
            StorageCell moved = Objects.requireNonNull(StorageCells.getCellInventory(result, null));
            Assertions.assertEquals(128, moved.getAvailableStacks().get(AEItemKey.of(Items.STONE)));
        }
    }
}
