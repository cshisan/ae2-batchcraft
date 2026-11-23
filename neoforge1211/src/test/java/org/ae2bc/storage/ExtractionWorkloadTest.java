package org.ae2bc.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import com.sun.management.ThreadMXBean;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

class ExtractionWorkloadTest {
    /* 相同多品种负载记录线程分配，断言只校验预算和公平性，不用耗时阈值。 */
    @Test
    void manyResourceTypesKeepBoundedFairProbes() {
        KeyCounter contents = new KeyCounter();
        for (int index = 0; index < 4096; index++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("workload " + index));
            contents.add(AEItemKey.of(stack), 1);
        }
        MEStorage source = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter counter) {
                counter.addAll(contents);
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                throw new AssertionError("Rejected candidates must not be extracted");
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        GenericStackInv destination = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        Map<AEKeyType, MEStorage> sources = Map.of(AEKeyType.items(), source);
        ProductExtractor.Cursor cursor = new ProductExtractor.Cursor();
        IActionSource actionSource = IActionSource.empty();
        Set<AEKey> visited = new HashSet<>();
        Predicate<AEKey> rejected = key -> {
            visited.add(key);
            return false;
        };
        Consumer<GenericStack> overflow = stack -> Assertions.fail("Unexpected overflow");
        for (int round = 0; round < 64; round++) {
            ProductExtractor.extract(sources, destination, 4096, 4096,
                    actionSource, rejected, overflow, null, cursor);
        }
        Assertions.assertEquals(4096, visited.size());
        ThreadMXBean allocation = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled() ? bean : null;
        long thread = Thread.currentThread().threadId();
        long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        int rounds = 256;
        for (int round = 0; round < rounds; round++) {
            ProductExtractor.Result result = ProductExtractor.extract(sources, destination, 4096, 4096,
                    actionSource, rejected, overflow, null, cursor);
            Assertions.assertEquals(0, result.insertedAmount());
        }
        long allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - before;
        System.out.printf(Locale.ROOT, "HOTPATH extraction types=4096 rounds=%d bytes_per_call=%.1f%n",
                rounds, allocated < 0 ? -1.0 : (double) allocated / rounds);
        Assertions.assertEquals(4096, contents.size());
        Assertions.assertTrue(destination.isEmpty());
    }
}
