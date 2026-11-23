package org.ae2bc.part;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpCraftingTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.part.general.Pp2mpResourceIntakeTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockMakers;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
class TargetCacheLifecycleTest {
    private static final AEKey STONE = AEItemKey.of(Items.STONE);

    @Test
    void multiMaterialPrecheckResolvesEachStorageTypeOnce() {
        Fixture fixture = new Fixture();
        fixture.handler = new ItemStackHandler(9);
        Pp2mpSendTarget send = fixture.observedSend();
        Map<AEKey, Long> requested = new LinkedHashMap<>();
        for (int index = 0; index < 9; index++) {
            ItemStack material = new ItemStack(Items.PAPER);
            material.set(DataComponents.CUSTOM_NAME, Component.literal("precheck " + index));
            requested.put(Objects.requireNonNull(AEItemKey.of(material)), 1L);
        }
        Assertions.assertTrue(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Assertions.assertEquals(0, fixture.stored());
        long resolutions = Mockito.mockingDetails(send).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("findStorage")).count();
        System.out.printf("HOTPATH acceptance materials=9 storage_resolutions=%d%n", resolutions);

        Mockito.clearInvocations(send);
        fixture.handler = new ItemStackHandler(1);
        fixture.invalidate();
        Assertions.assertFalse(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Assertions.assertEquals(0, fixture.stored());
    }

    @Test
    void mixedPrecheckResolvesEachTypeAndRetainsFluidCapacity() {
        Fixture fixture = new Fixture();
        fixture.handler = new ItemStackHandler(2);
        fixture.fluid = new FluidTank(1000);
        Pp2mpSendTarget send = fixture.observedSend();
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Map<AEKey, Long> requested = new LinkedHashMap<>();
        requested.put(TargetCacheLifecycleTest.STONE, 2L);
        requested.put(AEItemKey.of(Items.DIRT), 2L);
        requested.put(water, 1000L);
        Assertions.assertTrue(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.fluids());
        Assertions.assertEquals(0, fixture.stored());
        Assertions.assertTrue(fixture.fluid.isEmpty());

        Mockito.clearInvocations(send);
        requested.put(water, 1001L);
        Assertions.assertFalse(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.fluids());
        Assertions.assertEquals(0, fixture.stored());
        Assertions.assertTrue(fixture.fluid.isEmpty());
    }

    @Test
    void precheckStopsBeforeResolvingUnneededTypes() {
        Fixture fixture = new Fixture();
        fixture.handler = new ItemStackHandler(1);
        Pp2mpSendTarget send = fixture.observedSend();
        Assertions.assertTrue(send.canInsertAll(Map.of(), IActionSource.empty(), new Pp2mpInsertPlan()));
        Map<AEKey, Long> requested = new LinkedHashMap<>();
        requested.put(TargetCacheLifecycleTest.STONE, 0L);
        requested.put(AEFluidKey.of(Fluids.WATER), 1000L);
        for (long amount : new long[]{0, -1}) {
            requested.put(TargetCacheLifecycleTest.STONE, amount);
            Assertions.assertFalse(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        }
        Mockito.verify(send, Mockito.never()).findStorage(Mockito.any(AEKeyType.class));

        requested.put(TargetCacheLifecycleTest.STONE, 65L);
        Assertions.assertFalse(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Mockito.verify(send, Mockito.never()).findStorage(AEKeyType.fluids());
        Assertions.assertEquals(0, fixture.stored());

        Mockito.clearInvocations(send);
        fixture.loaded = false;
        fixture.invalidate();
        Assertions.assertFalse(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        Mockito.verify(send, Mockito.times(1)).findStorage(AEKeyType.items());
        Mockito.verify(send, Mockito.never()).findStorage(AEKeyType.fluids());
    }

    @Test
    void singleMaterialPrecheckQueriesOnEveryCallWithoutChangingInventory() {
        Fixture fixture = new Fixture();
        Pp2mpSendTarget send = fixture.observedSend();
        Map<AEKey, Long> requested = Map.of(TargetCacheLifecycleTest.STONE, 2L);
        for (int index = 0; index < 2; index++) {
            Assertions.assertTrue(send.canInsertAll(requested, IActionSource.empty(), new Pp2mpInsertPlan()));
        }
        Mockito.verify(send, Mockito.times(2)).findStorage(AEKeyType.items());
        Assertions.assertEquals(0, fixture.stored());
    }

    @Test
    void batchCapacityUsesNativeHandlerAndSharesReservationsAcrossTargets() {
        Fixture fixture = new Fixture();
        fixture.handler = new ItemStackHandler(1);
        AEKey dirt = AEItemKey.of(Items.DIRT);
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertTrue(fixture.send.canInsertAll(
                Map.of(STONE, 2L), IActionSource.empty(), plan));
        Pp2mpSendTarget another = new Pp2mpSendTarget(fixture.adjacent, () -> { });
        Assertions.assertFalse(another.canInsertAll(
                Map.of(dirt, 2L), IActionSource.empty(), plan));
        Assertions.assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
        fixture.handler = new ItemStackHandler(2);
        fixture.invalidate();
        Assertions.assertTrue(fixture.send.canInsertAll(
                Map.of(STONE, 2L, dirt, 2L), IActionSource.empty(), new Pp2mpInsertPlan()));
    }

    @Test
    void singleInsertionKeepsNativeLimitWithoutLimitingCustomStorageOrWholePlan() {
        Fixture fixture = new Fixture();
        fixture.handler = new ItemStackHandler(2) {
            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return Integer.MAX_VALUE;
            }
        };
        long large = (long) Integer.MAX_VALUE + 10;
        Assertions.assertEquals(Integer.MAX_VALUE, fixture.send.reserve(
                new Pp2mpInsertPlan(), TargetCacheLifecycleTest.STONE, large, IActionSource.empty()));
        Assertions.assertTrue(fixture.send.canInsertAll(
                Map.of(TargetCacheLifecycleTest.STONE, large), IActionSource.empty(), new Pp2mpInsertPlan()));
        fixture.direct = Mockito.mock(MEStorage.class);
        Mockito.when(fixture.direct.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                        Mockito.eq(Actionable.SIMULATE), Mockito.any(IActionSource.class)))
                .thenAnswer(call -> call.getArgument(1, Long.class));
        fixture.invalidate();
        Assertions.assertEquals(large, fixture.send.reserve(
                new Pp2mpInsertPlan(), TargetCacheLifecycleTest.STONE, large, IActionSource.empty()));
    }

    @Test
    void customStorageKeepsIndependentSimulationBeforeAccumulatedReservation() {
        Fixture fixture = new Fixture();
        fixture.direct = Mockito.mock(MEStorage.class);
        List<Long> requests = new ArrayList<>();
        Mockito.when(fixture.direct.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                        Mockito.eq(Actionable.SIMULATE), Mockito.any(IActionSource.class)))
                .thenAnswer(call -> {
                    long amount = call.getArgument(1, Long.class);
                    requests.add(amount);
                    return amount <= 3 ? amount : amount / 2;
                });
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(2, fixture.send.reserve(
                plan, TargetCacheLifecycleTest.STONE, 2, IActionSource.empty()));
        Assertions.assertEquals(0, fixture.send.reserve(
                plan, TargetCacheLifecycleTest.STONE, 6, IActionSource.empty()));
        Assertions.assertEquals(List.of(2L, 2L, 6L, 5L), requests);
    }

    @Test
    void fluidReservationKeepsWholeFillRestrictions() {
        Fixture fixture = new Fixture();
        List<Integer> requests = new ArrayList<>();
        fixture.fluid = new FluidTank(1000) {
            @Override
            public int fill(@NonNull FluidStack resource, @NonNull FluidAction action) {
                Assertions.assertEquals(FluidAction.SIMULATE, action);
                int amount = resource.getAmount();
                requests.add(amount);
                return amount <= 3 ? amount : amount / 2;
            }
        };
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Pp2mpInsertPlan plan = new Pp2mpInsertPlan();
        Assertions.assertEquals(2, fixture.send.reserve(plan, water, 2, IActionSource.empty()));
        Assertions.assertEquals(0, fixture.send.reserve(plan, water, 6, IActionSource.empty()));
        Assertions.assertEquals(List.of(2, 2, 6, 5), requests);
        Assertions.assertTrue(fixture.fluid.isEmpty());
    }

    @Test
    void sendingResolvesOnlyRequestedTypesAndKeepsMixedResources() {
        Fixture fixture = new Fixture();
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Map<AEKeyType, MEStorage> items = fixture.send.findStorages(
                List.of(TargetCacheLifecycleTest.STONE, AEItemKey.of(Items.DIRT)));
        Assertions.assertEquals(1, items.size());
        Assertions.assertNotNull(items.get(AEKeyType.items()));
        Mockito.verify(fixture.level, Mockito.never()).getCapability(
                Mockito.eq(Capabilities.FluidHandler.BLOCK), Mockito.any(BlockPos.class), Mockito.any(Direction.class));
        fixture.fluid = new FluidTank(1000);
        fixture.invalidate();
        Map<AEKeyType, MEStorage> mixed = fixture.send.findStorages(List.of(TargetCacheLifecycleTest.STONE, water));
        Assertions.assertEquals(2, mixed.size());
        Assertions.assertEquals(1000, Pp2mpSendTarget.insert(
                mixed, water, 1500, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(2, Pp2mpSendTarget.insert(
                mixed, TargetCacheLifecycleTest.STONE, 2, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertTrue(fixture.send.hasStorage());
        fixture.direct = Mockito.mock(MEStorage.class);
        fixture.invalidate();
        Map<AEKeyType, MEStorage> direct = fixture.send.findStorages(List.of(TargetCacheLifecycleTest.STONE, water));
        Assertions.assertSame(fixture.direct, direct.get(AEKeyType.items()));
        Assertions.assertSame(fixture.direct, direct.get(AEKeyType.fluids()));
        fixture.loaded = false;
        fixture.invalidate();
        Assertions.assertFalse(fixture.send.hasStorage());
    }

    @Test
    void repeatedRealTransfersReuseListenersAndNotifyWithoutClearingTarget() {
        Fixture fixture = new Fixture();
        Assertions.assertNotNull(fixture.send.findStorage(AEKeyType.items()));
        int listeners = fixture.listeners.size();
        Pp2mpAdjacentTarget.Target target = fixture.adjacent.resolve();
        for (int index = 0; index < 128; index++) {
            Assertions.assertEquals(2, fixture.send.insert(
                    TargetCacheLifecycleTest.STONE, 2, Actionable.SIMULATE, IActionSource.empty()));
            Assertions.assertEquals(2, fixture.send.insert(
                    TargetCacheLifecycleTest.STONE, 2, Actionable.MODULATE, IActionSource.empty()));
        }
        Assertions.assertEquals(128, fixture.notifications);
        Assertions.assertEquals(listeners, fixture.listeners.size());
        Assertions.assertSame(target, fixture.adjacent.resolve());
        Assertions.assertEquals(256, fixture.stored());
    }

    @Test
    void replacedHandlerAndChunkReloadRefreshCapabilitiesWithoutNewListeners() {
        Fixture fixture = new Fixture();
        fixture.send.insert(TargetCacheLifecycleTest.STONE, 2, Actionable.MODULATE, IActionSource.empty());
        ItemStackHandler previous = fixture.handler;
        int listeners = fixture.listeners.size();
        fixture.handler = new ItemStackHandler(8);
        fixture.invalidate();
        Assertions.assertEquals(3, fixture.send.insert(
                TargetCacheLifecycleTest.STONE, 3, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(2, previous.getStackInSlot(0).getCount());
        Assertions.assertEquals(3, fixture.stored());

        fixture.loaded = false;
        fixture.invalidate();
        Assertions.assertNull(fixture.send.findStorage(AEKeyType.items()));
        fixture.loaded = true;
        fixture.invalidate();
        Assertions.assertNotNull(fixture.send.findStorage(AEKeyType.items()));
        Assertions.assertEquals(listeners, fixture.listeners.size());
    }

    @Test
    void directStorageCanAppearAndDisappearAtTheSameAddress() {
        Fixture fixture = new Fixture();
        Assertions.assertNotNull(fixture.send.findStorage(AEKeyType.items()));
        int listeners = fixture.listeners.size();
        fixture.direct = Mockito.mock(MEStorage.class);
        fixture.invalidate();
        Assertions.assertSame(fixture.direct, fixture.send.findStorage(AEKeyType.items()));
        fixture.direct = null;
        fixture.invalidate();
        Assertions.assertEquals(2, fixture.send.insert(
                TargetCacheLifecycleTest.STONE, 2, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(listeners, fixture.listeners.size());
    }

    @Test
    void fluidFallbackRefreshesReplacedTankAndHonorsActualCapacity() {
        Fixture fixture = new Fixture();
        fixture.fluid = new FluidTank(4000);
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Assertions.assertEquals(1000, fixture.send.insert(water, 1000, Actionable.MODULATE, IActionSource.empty()));
        FluidTank previous = fixture.fluid;
        int listeners = fixture.listeners.size();
        fixture.fluid = new FluidTank(500);
        fixture.invalidate();
        Assertions.assertEquals(500, fixture.send.insert(water, 1000, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(1000, previous.getFluidAmount());
        Assertions.assertEquals(500, fixture.fluid.getFluidAmount());
        Assertions.assertEquals(listeners, fixture.listeners.size());
    }

    @Test
    void addressAndSideChangesRebuildOnlyOnNextAccessAndRetireOldListener() {
        Fixture fixture = new Fixture();
        fixture.send.findStorage(AEKeyType.items());
        int listeners = fixture.listeners.size();
        ICapabilityInvalidationListener previous = fixture.listeners.getFirst();
        Pp2mpAdjacentTarget.Target snapshot = Objects.requireNonNull(fixture.adjacent.resolve());
        fixture.side = Direction.SOUTH;
        fixture.send.findStorage(AEKeyType.items());
        Assertions.assertEquals(listeners * 2, fixture.listeners.size());
        Assertions.assertFalse(fixture.adjacent.isCurrent(snapshot));
        Assertions.assertFalse(previous.onInvalidate());
        fixture.pos = BlockPos.ZERO.east();
        fixture.send.findStorage(AEKeyType.items());
        Assertions.assertEquals(listeners * 3, fixture.listeners.size());
    }

    @Test
    void explicitReleaseDoesNotReactivateOldListenerWhenSameAddressIsReused() {
        Fixture fixture = new Fixture();
        fixture.send.findStorage(AEKeyType.items());
        ICapabilityInvalidationListener previous = fixture.listeners.getFirst();
        fixture.send.clear();
        fixture.send.findStorage(AEKeyType.items());
        Assertions.assertFalse(previous.onInvalidate());
        Assertions.assertEquals(0, fixture.notifications);
    }

    @Test
    void craftingCapabilityInvalidationNotifiesWithoutQueryingInsideCallback() {
        Fixture fixture = new Fixture();
        Pp2mpCraftingTarget crafting = new Pp2mpCraftingTarget(fixture.adjacent, () -> fixture.notifications++);
        Assertions.assertNull(crafting.findMachine());
        fixture.machine = Mockito.mock(ICraftingMachine.class);
        Mockito.clearInvocations(fixture.level);
        fixture.invalidate();
        Mockito.verifyNoInteractions(fixture.level);
        Assertions.assertEquals(1, fixture.notifications);
        Assertions.assertSame(fixture.machine, crafting.findMachine());
        Assertions.assertEquals(1, fixture.listeners.size());
        crafting.clear();
        crafting.findMachine();
        Assertions.assertFalse(fixture.listeners.getFirst().onInvalidate());
    }

    @Test
    void sourceGenerationChangesForInvalidationButNotForOrdinaryExtraction() {
        Fixture fixture = new Fixture();
        Pp2mpResourceIntakeTarget source = new Pp2mpResourceIntakeTarget(
                fixture.adjacent, () -> fixture.notifications++, () -> fixture.notifications++);
        fixture.handler.setStackInSlot(0, new ItemStack(Items.STONE, 8));
        long generation = source.generation();
        MEStorage storage = Objects.requireNonNull(source.findSource(AEKeyType.items()));
        int listeners = fixture.listeners.size();
        Assertions.assertEquals(2, storage.extract(
                TargetCacheLifecycleTest.STONE, 2, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(generation, source.generation());
        fixture.invalidate();
        Assertions.assertTrue(source.generation() > generation);
        Assertions.assertNotNull(source.findSource(AEKeyType.items()));
        Assertions.assertEquals(listeners, fixture.listeners.size());
    }

    private static final class Fixture {
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final List<ICapabilityInvalidationListener> listeners = new ArrayList<>();
        private final Pp2mpAdjacentTarget adjacent = new Pp2mpAdjacentTarget(
                () -> this.level, () -> this.pos, () -> this.side);
        private final Pp2mpSendTarget send = new Pp2mpSendTarget(this.adjacent, () -> this.notifications++);
        private BlockPos pos = BlockPos.ZERO;
        private Direction side = Direction.NORTH;
        private ItemStackHandler handler = new ItemStackHandler(8);
        private MEStorage direct;
        private FluidTank fluid;
        private ICraftingMachine machine;
        private boolean loaded = true;
        private int notifications;

        private Fixture() {
            Mockito.when(this.level.isLoaded(Mockito.any(BlockPos.class))).thenAnswer(call -> this.loaded);
            Mockito.when(this.level.getCapability(Mockito.eq(AECapabilities.ME_STORAGE),
                    Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenAnswer(call -> this.direct);
            Mockito.when(this.level.getCapability(Mockito.eq(Capabilities.ItemHandler.BLOCK),
                    Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenAnswer(call -> this.handler);
            Mockito.when(this.level.getCapability(Mockito.eq(Capabilities.FluidHandler.BLOCK),
                    Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenAnswer(call -> this.fluid);
            Mockito.when(this.level.getCapability(Mockito.eq(AECapabilities.CRAFTING_MACHINE),
                    Mockito.any(BlockPos.class), Mockito.any(Direction.class))).thenAnswer(call -> this.machine);
            Mockito.doAnswer(call -> {
                this.listeners.add(call.getArgument(1, ICapabilityInvalidationListener.class));
                return null;
            }).when(this.level).registerCapabilityListener(
                    Mockito.any(BlockPos.class), Mockito.any(ICapabilityInvalidationListener.class));
        }

        private void invalidate() {
            for (ICapabilityInvalidationListener listener : List.copyOf(this.listeners)) {
                listener.onInvalidate();
            }
        }

        private Pp2mpSendTarget observedSend() {
            return Mockito.mock(Pp2mpSendTarget.class, Mockito.withSettings()
                    .mockMaker(MockMakers.INLINE).spiedInstance(this.send).defaultAnswer(Mockito.CALLS_REAL_METHODS));
        }

        private int stored() {
            int result = 0;
            for (int slot = 0; slot < this.handler.getSlots(); slot++) {
                result += this.handler.getStackInSlot(slot).getCount();
            }
            return result;
        }
    }
}
