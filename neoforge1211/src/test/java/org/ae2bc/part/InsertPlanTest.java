package org.ae2bc.part;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InsertPlanTest {
    @Test
    void differentResourcesCannotReserveTheSameEmptySlot() {
        ItemStackHandler handler = new ItemStackHandler(1);
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(2, plan.reserve(handler, AEItemKey.of(Items.STONE), 2));
        Assertions.assertEquals(0, plan.reserve(handler, AEItemKey.of(Items.DIRT), 2));
        Assertions.assertEquals(62, plan.reserve(handler, AEItemKey.of(Items.STONE), 64));
        Assertions.assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test
    void itemSimulationHonorsActualSlotLimitsAndExistingStacks() {
        ItemStackHandler handler = new ItemStackHandler(2);
        handler.setStackInSlot(0, new ItemStack(Items.STONE, 63));
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(1, plan.reserve(handler, AEItemKey.of(Items.STONE), 1));
        Assertions.assertEquals(64, plan.reserve(handler, AEItemKey.of(Items.DIRT), 64));
        Assertions.assertEquals(0, plan.reserve(handler, AEItemKey.of(Items.STONE), 1));
        Assertions.assertEquals(63, handler.getStackInSlot(0).getCount());
        Assertions.assertTrue(handler.getStackInSlot(1).isEmpty());
    }

    @Test
    void largeMachineSlotsDoNotGetReducedToVanillaStackSize() {
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return 69632;
            }
        };
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(69632, plan.reserve(handler, AEItemKey.of(Items.COBBLESTONE), 108800));
        Assertions.assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test
    void fluidSimulationReservesTankIdentityAndCapacity() {
        FluidTank handler = new FluidTank(1000);
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(600, plan.reserve(handler, AEFluidKey.of(Fluids.WATER), 600));
        Assertions.assertEquals(0, plan.reserve(handler, AEFluidKey.of(Fluids.LAVA), 100));
        Assertions.assertEquals(400, plan.reserve(handler, AEFluidKey.of(Fluids.WATER), 600));
        Assertions.assertTrue(handler.isEmpty());
    }

    @Test
    void mixedBackendsKeepReservationsWithinOnePlan() {
        ItemStackHandler items = new ItemStackHandler(1);
        FluidTank fluids = new FluidTank(1000);
        MEStorage generic = Mockito.mock(MEStorage.class);
        AEItemKey stone = AEItemKey.of(Items.STONE);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        IActionSource source = IActionSource.empty();
        Mockito.when(generic.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                        Mockito.eq(Actionable.SIMULATE), Mockito.same(source)))
                .thenAnswer(call -> Math.min(10L, call.getArgument(1, Long.class)));
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(6, plan.reserve(generic, stone, 6, source));
        Assertions.assertEquals(60, plan.reserve(items, stone, 60));
        Assertions.assertEquals(900, plan.reserve(fluids, water, 900));
        Assertions.assertEquals(4, plan.reserve(generic, stone, 6, source));
        Assertions.assertEquals(4, plan.reserve(items, stone, 8));
        Assertions.assertEquals(100, plan.reserve(fluids, water, 200));
        Pp2mpInsertPlan next = new Pp2mpInsertPlan();
        Assertions.assertEquals(10, next.reserve(generic, stone, 10, source));
        Assertions.assertEquals(64, next.reserve(items, stone, 64));
        Assertions.assertEquals(1000, next.reserve(fluids, water, 1000));
        Assertions.assertTrue(items.getStackInSlot(0).isEmpty());
        Assertions.assertTrue(fluids.isEmpty());
    }
}
