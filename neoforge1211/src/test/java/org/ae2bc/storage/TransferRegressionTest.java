package org.ae2bc.storage;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.pattern.PatternTestSupport;
import org.ae2bc.pattern.Pp2mpTaskPlan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class TransferRegressionTest {
    @Test
    void mergedMaterialsFeedOneCompleteRecipeInsteadOfFillingSharedCapacityWithFirstIngredient() {
        AEKey first = AEItemKey.of(Items.STONE);
        AEKey second = AEItemKey.of(Items.DIRT);
        IPatternDetails pattern = PatternTestSupport.processing(
                PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(first, 2), new GenericStack(second, 2)),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))));
        KeyCounter[] received = {new KeyCounter(), new KeyCounter()};
        received[0].add(first, 128);
        received[1].add(second, 128);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(pattern, received, null);
        Pp2mpInputData input = new Pp2mpInputData(() -> {
        }, () -> {
        });
        input.setBatchTask(plan.pattern(), plan.inputs(), plan.totalUnits(), UUID.randomUUID());
        KeyCounter[] batch = Pp2mpTaskPlan.nextInputs(
                Pp2mpTaskPlan.restore(plan.pattern(), null), input.getBatchInputs());
        Map<AEKey, Long> pending = Pp2mpTaskPlan.amounts(batch);
        Map<AEKey, Long> machine = new LinkedHashMap<>();
        MEStorage storage = new MEStorage() {
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                long occupied = machine.values().stream().mapToLong(Long::longValue).sum();
                long inserted = Math.min(amount, 4 - occupied);
                if (mode == Actionable.MODULATE && inserted > 0) {
                    machine.merge(key, inserted, Long::sum);
                }
                return inserted;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        input.setBatchInFlight("endpoint", 1, 0);
        input.commitBatchInputs(batch);
        input.clearBatchInFlight();
        Assertions.assertTrue(Pp2mpSendTarget.drainPending(
                pending, Map.of(AEKeyType.items(), storage), IActionSource.empty(), () -> {
                }));
        Assertions.assertEquals(Map.of(first, 2L, second, 2L), machine);
        Assertions.assertTrue(pending.isEmpty());
        Assertions.assertEquals(63, input.getBatchRemainingUnits());
        Assertions.assertEquals(Map.of(first, 126L, second, 126L),
                Pp2mpTaskPlan.amounts(input.getBatchInputs()));
    }

    @Test
    void competingInputsKeepOnlyUnsentRemainderAndNeverResendCommittedMaterial() {
        AEKey first = AEItemKey.of(Items.STONE);
        AEKey second = AEItemKey.of(Items.DIRT);
        OneSlotStorage storage = new OneSlotStorage();
        Map<AEKeyType, MEStorage> targets = Map.of(AEKeyType.items(), storage);
        IActionSource source = IActionSource.empty();
        Assertions.assertEquals(8, Pp2mpSendTarget.insert(targets, first, 8, Actionable.SIMULATE, source));
        Assertions.assertEquals(8, Pp2mpSendTarget.insert(targets, second, 8, Actionable.SIMULATE, source));
        Map<AEKey, Long> pending = new LinkedHashMap<>();
        pending.put(first, 8L);
        pending.put(second, 8L);
        AtomicInteger saves = new AtomicInteger();

        Assertions.assertTrue(Pp2mpSendTarget.drainPending(pending, targets, source, saves::incrementAndGet));
        Assertions.assertEquals(Map.of(second, 8L), pending);
        Assertions.assertFalse(Pp2mpSendTarget.drainPending(pending, targets, source, saves::incrementAndGet));
        storage.occupied = false;
        Assertions.assertTrue(Pp2mpSendTarget.drainPending(pending, targets, source, saves::incrementAndGet));
        Assertions.assertTrue(pending.isEmpty());
        Assertions.assertFalse(Pp2mpSendTarget.drainPending(pending, targets, source, saves::incrementAndGet));
        Assertions.assertEquals(Map.of(first, 8L, second, 8L), storage.committed);
        Assertions.assertEquals(2, saves.get());
    }

    @Test
    void reducedActualCapacityDeductsOnlyRealInsertions() {
        AEKey key = AEItemKey.of(Items.STONE);
        OneSlotStorage storage = new OneSlotStorage();
        storage.actualLimit = 5;
        Map<AEKeyType, MEStorage> targets = Map.of(AEKeyType.items(), storage);
        Map<AEKey, Long> pending = new LinkedHashMap<>(Map.of(key, 8L));
        Assertions.assertEquals(8, Pp2mpSendTarget.insert(targets, key, 8, Actionable.SIMULATE, IActionSource.empty()));
        Pp2mpSendTarget.drainPending(pending, targets, IActionSource.empty(), () -> {
        });
        Assertions.assertEquals(3L, pending.get(key));
        Assertions.assertEquals(5L, storage.committed.get(key));
    }

    @Test
    void laterTargetFailureStillMarksEarlierCommitForSaving() {
        AEKey first = AEItemKey.of(Items.STONE);
        AEKey second = AEItemKey.of(Items.DIRT);
        OneSlotStorage storage = new OneSlotStorage();
        storage.failOn = second;
        Map<AEKey, Long> pending = new LinkedHashMap<>();
        pending.put(first, 4L);
        pending.put(second, 9L);
        AtomicInteger saves = new AtomicInteger();
        Assertions.assertThrows(IllegalStateException.class, () -> Pp2mpSendTarget.drainPending(
                pending, Map.of(AEKeyType.items(), storage), IActionSource.empty(), saves::incrementAndGet));
        Assertions.assertEquals(Map.of(second, 9L), pending);
        Assertions.assertEquals(1, saves.get());
    }

    @Test
    void canceledInputsBypassProductFilterWithoutCountingAsProducts() {
        GenericStackInv destination = new GenericStackInv(AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 1);
        AtomicLong countedProducts = new AtomicLong();
        ReceiveInventory inventory = new ReceiveInventory(() -> destination, Map::of, key -> false,
                (key, amount) -> countedProducts.addAndGet(amount), IActionSource.empty(), () -> {
        });
        AEKey key = AEItemKey.of(Items.STONE);
        Assertions.assertEquals(0, inventory.insert(key, 8, Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(8, inventory.insertUntracked(key, 8));
        Assertions.assertEquals(8, destination.getAmount(0));
        Assertions.assertEquals(0, countedProducts.get());
    }

    @Test
    void recoveryQueueRetainsPartialRemainderAndOnlyCountsAcceptedProducts() {
        AEKey key = AEItemKey.of(Items.STONE);
        OneSlotStorage destination = new OneSlotStorage();
        destination.actualLimit = 5;
        AtomicLong countedProducts = new AtomicLong();
        ReceiveInventory inventory = new ReceiveInventory(
                () -> null, () -> Map.of(AEKeyType.items(), (extractable, changed) -> destination),
                candidate -> true, (candidate, amount) -> countedProducts.addAndGet(amount),
                IActionSource.empty(), () -> {
        });
        Map<AEKey, Long> recovery = new LinkedHashMap<>(Map.of(key, 8L));

        Assertions.assertTrue(inventory.drainRecovery(recovery));
        Assertions.assertEquals(Map.of(key, 3L), recovery);
        Assertions.assertEquals(5, countedProducts.get());
        Assertions.assertFalse(inventory.drainRecovery(recovery));
        destination.occupied = false;
        Assertions.assertTrue(inventory.drainRecovery(recovery));
        Assertions.assertTrue(recovery.isEmpty());
        Assertions.assertEquals(8, countedProducts.get());
        Assertions.assertEquals(Map.of(key, 8L), destination.committed);
    }

    @Test
    void untrackedRecoverySavesSettledQueueAndDoesNotCountProducts() {
        GenericStackInv destination = new GenericStackInv(
                AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 1);
        AtomicLong countedProducts = new AtomicLong();
        AtomicInteger saves = new AtomicInteger();
        ReceiveInventory inventory = new ReceiveInventory(() -> destination, Map::of, key -> false,
                (key, amount) -> countedProducts.addAndGet(amount), IActionSource.empty(), () -> {
        });
        AEKey key = AEItemKey.of(Items.STONE);
        Map<AEKey, Long> recovery = new LinkedHashMap<>(Map.of(key, 8L));

        Assertions.assertTrue(inventory.drainUntracked(recovery, () -> {
            Assertions.assertTrue(recovery.isEmpty());
            Assertions.assertEquals(8, destination.getAmount(0));
            saves.incrementAndGet();
        }));
        Assertions.assertFalse(inventory.drainUntracked(recovery, saves::incrementAndGet));
        Assertions.assertEquals(1, saves.get());
        Assertions.assertEquals(0, countedProducts.get());
        Assertions.assertNull(inventory.getStack(0));
        Assertions.assertFalse(inventory.canExtract());
        Assertions.assertEquals(0, inventory.extract(0, key, 8, Actionable.MODULATE));
    }

    @Test
    void sharedStorageIsEnumeratedOnceForItemAndFluidExtraction() {
        AEKey item = AEItemKey.of(Items.STONE);
        AEKey fluid = AEFluidKey.of(Fluids.WATER);
        Map<AEKey, Long> remaining = new LinkedHashMap<>(Map.of(item, 8L, fluid, 1000L));
        AtomicInteger enumerations = new AtomicInteger();
        MEStorage source = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter counter) {
                enumerations.incrementAndGet();
                remaining.forEach(counter::add);
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                long taken = Math.min(amount, remaining.getOrDefault(key, 0L));
                if (mode == Actionable.MODULATE) {
                    remaining.merge(key, -taken, Long::sum);
                }
                return taken;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        GenericStackInv destination = new GenericStackInv(AEKeyTypes.getAll(), () -> {
        }, GenericStackInv.Mode.STORAGE, 2);
        ProductExtractor.extract(Map.of(AEKeyType.items(), source, AEKeyType.fluids(), source), destination,
                100, 10, IActionSource.empty(), key -> true, stack -> Assertions.fail("Unexpected overflow"));
        Assertions.assertEquals(0L, remaining.get(item));
        Assertions.assertEquals(0L, remaining.get(fluid));
        Assertions.assertEquals(1, enumerations.get());
    }

    private static final class OneSlotStorage implements MEStorage {
        private final Map<AEKey, Long> committed = new LinkedHashMap<>();
        private boolean occupied;
        private long actualLimit = Long.MAX_VALUE;
        private AEKey failOn;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (key.equals(this.failOn)) {
                throw new IllegalStateException("Test target failed");
            }
            if (this.occupied) {
                return 0;
            }
            if (mode == Actionable.SIMULATE) {
                return amount;
            }
            long inserted = Math.min(amount, this.actualLimit);
            this.committed.merge(key, inserted, Long::sum);
            this.occupied = true;
            return inserted;
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    }
}
