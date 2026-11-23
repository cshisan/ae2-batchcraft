package org.ae2bc.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.ae2bc.integration.ImmediatePatternProviderReturnInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ReturnBatchTest {
    @Test
    void failedTargetLookupReleasesTheTaskScopeBeforeRetrying() {
        AtomicInteger scopes = new AtomicInteger();
        AtomicInteger queries = new AtomicInteger();
        FlushingInventory target = new FlushingInventory(1);
        ReceiveInventory inventory = new ReceiveInventory(() -> {
            if (queries.getAndIncrement() == 0) {
                throw new IllegalStateException("Test target lookup failed");
            }
            return target;
        }, Map::of, key -> true, (key, amount) -> { }, IActionSource.empty(), () -> { },
                scopes::incrementAndGet, scopes::decrementAndGet);
        Assertions.assertThrows(IllegalStateException.class, inventory::beginBatch);
        Assertions.assertEquals(0, scopes.get());
        Assertions.assertEquals(1, inventory.insert(AEItemKey.of(Items.STONE), 1,
                Actionable.MODULATE, IActionSource.empty()));
        Assertions.assertEquals(0, scopes.get());
        Assertions.assertEquals(1, target.flushes);
    }

    @Test
    void nestedCompletionKeepsOneTargetAndFlushesOnlyAfterRecoveryAndTicketsSettle() {
        FlushingInventory target = new FlushingInventory(3);
        AEKey first = AEItemKey.of(Items.STONE);
        AEKey second = AEItemKey.of(Items.DIRT);
        AEKey ticket = AEItemKey.of(Items.PAPER);
        Map<AEKey, Long> recovery = new LinkedHashMap<>(Map.of(first, 2L, second, 3L));
        AtomicInteger queries = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger scopes = new AtomicInteger();
        AtomicReference<ReceiveInventory> receiver = new AtomicReference<>();
        ReceiveInventory inventory = new ReceiveInventory(() -> {
            queries.incrementAndGet();
            return target;
        }, Map::of, key -> true, (key, amount) -> { }, IActionSource.empty(), () -> { },
                scopes::incrementAndGet, () -> {
                    Assertions.assertTrue(recovery.isEmpty());
                    receiver.get().insert(ticket, 1, Actionable.MODULATE, IActionSource.empty());
                    scopes.decrementAndGet();
                });
        receiver.set(inventory);
        target.ae2bc$setImmediateFlushHandler(() -> {
            Assertions.assertEquals(0, scopes.get());
            Assertions.assertEquals(2, saves.get());
            Assertions.assertTrue(recovery.isEmpty());
        });
        inventory.drainRecovery(recovery, saves::incrementAndGet);
        Assertions.assertEquals(1, queries.get());
        Assertions.assertEquals(1, target.flushes);
        Assertions.assertEquals(Map.of(first, 2L, second, 3L, ticket, 1L), target.returned);
    }

    @Test
    void fullReturnInventoryFlushesBetweenSettledResourcesWithoutEndingTheScope() {
        FlushingInventory target = new FlushingInventory(1);
        Map<AEKey, Long> stock = new LinkedHashMap<>();
        stock.put(AEItemKey.of(Items.STONE), 2L);
        stock.put(AEItemKey.of(Items.DIRT), 3L);
        AtomicInteger scopes = new AtomicInteger();
        MEStorage source = ReturnBatchTest.source(stock);
        ReceiveInventory inventory = new ReceiveInventory(() -> target, Map::of, key -> true,
                (key, amount) -> { }, IActionSource.empty(), () -> { },
                scopes::incrementAndGet, scopes::decrementAndGet);
        target.ae2bc$setImmediateFlushHandler(() -> {
            for (int slot = 0; slot < target.size(); slot++) {
                GenericStack stack = target.getStack(slot);
                if (stack != null) {
                    Assertions.assertEquals(0, stock.get(stack.what()));
                }
            }
            Assertions.assertEquals(target.flushes == 1 ? 1 : 0, scopes.get());
        });
        ProductExtractor.Result result = ProductExtractor.extract(
                Map.of(AEKeyType.items(), source), inventory, 10, 10, IActionSource.empty(),
                key -> true, stack -> Assertions.fail("Unexpected overflow"));
        Assertions.assertEquals(5, result.insertedAmount());
        Assertions.assertEquals(2, target.flushes);
        Assertions.assertEquals(5, target.returned.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void simulationNeverStartsARealReturnOrFlush() {
        FlushingInventory target = new FlushingInventory(1);
        ReceiveInventory inventory = new ReceiveInventory(() -> target, Map::of, key -> true,
                (key, amount) -> Assertions.fail("Simulation counted"),
                IActionSource.empty(), () -> { },
                () -> Assertions.fail("Simulation opened a real return"), () -> { });
        Assertions.assertEquals(8, inventory.insert(AEItemKey.of(Items.STONE), 8,
                Actionable.SIMULATE, IActionSource.empty()));
        Assertions.assertEquals(0, target.flushes);
        Assertions.assertNull(target.getStack(0));
    }

    @Test
    void largeRejectedInventoryUsesBoundedFairProbes() {
        Map<AEKey, Long> stock = new LinkedHashMap<>();
        for (int index = 0; index < 600; index++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("candidate " + index));
            stock.put(AEItemKey.of(stack), 1L);
        }
        MEStorage source = ReturnBatchTest.source(stock);
        GenericStackInv destination = new GenericStackInv(
                AEKeyTypes.getAll(), () -> { }, GenericStackInv.Mode.STORAGE, 1);
        ProductExtractor.Cursor cursor = new ProductExtractor.Cursor();
        Set<AEKey> visited = new HashSet<>();
        for (int attempt = 0; attempt < 3; attempt++) {
            AtomicInteger probes = new AtomicInteger();
            ProductExtractor.extract(Map.of(AEKeyType.items(), source), destination, 1000, 1000,
                    IActionSource.empty(), key -> {
                        probes.incrementAndGet();
                        visited.add(key);
                        return false;
                    }, stack -> Assertions.fail("Unexpected overflow"), null, cursor);
            Assertions.assertEquals(256, probes.get());
        }
        Assertions.assertEquals(600, visited.size());
        Assertions.assertEquals(600, stock.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void zeroAmountEntriesDoNotConsumeThePositiveCandidateBudget() {
        Map<AEKey, Long> stock = new LinkedHashMap<>();
        for (int index = 0; index < 600; index++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("zero candidate " + index));
            stock.put(AEItemKey.of(stack), index < 550 ? 0L : 1L);
        }
        GenericStackInv destination = new GenericStackInv(
                AEKeyTypes.getAll(), null, GenericStackInv.Mode.STORAGE, 50);
        ProductExtractor.Result result = ProductExtractor.extract(
                Map.of(AEKeyType.items(), ReturnBatchTest.source(stock)), destination, 1000, 1000,
                IActionSource.empty(), key -> true, stack -> Assertions.fail("Unexpected overflow"));
        Assertions.assertEquals(50, result.insertedAmount());
        Assertions.assertEquals(0, stock.values().stream().mapToLong(Long::longValue).sum());
        Assertions.assertEquals(50, destination.getAvailableStacks().size());
    }

    private static MEStorage source(Map<AEKey, Long> stock) {
        return new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter counter) {
                stock.forEach(counter::add);
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long taken = Math.min(amount, stock.getOrDefault(key, 0L));
                if (mode == Actionable.MODULATE) {
                    stock.merge(key, -taken, Long::sum);
                }
                return taken;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
    }

    private static final class FlushingInventory extends GenericStackInv
            implements ImmediatePatternProviderReturnInventory {
        private final Map<AEKey, Long> returned = new LinkedHashMap<>();
        private int flushes;
        private Runnable handler = () -> { };

        private FlushingInventory(int size) {
            super(AEKeyTypes.getAll(), () -> { }, Mode.STORAGE, size);
        }

        @Override
        public void ae2bc$setImmediateFlushHandler(Runnable handler) {
            this.handler = handler;
        }

        @Override
        public void ae2bc$requestImmediateFlush() {
            this.flushes++;
            this.handler.run();
            for (int slot = 0; slot < this.size(); slot++) {
                GenericStack stack = this.getStack(slot);
                if (stack != null) {
                    this.returned.merge(stack.what(), stack.amount(), Math::addExact);
                    this.setStack(slot, null);
                }
            }
        }
    }
}
