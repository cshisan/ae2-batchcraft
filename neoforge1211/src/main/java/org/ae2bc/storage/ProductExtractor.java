package org.ae2bc.storage;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.ae2bc.common.Utils;
import org.ae2bc.core.dispatch.WorkBudget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("UnstableApiUsage")
public final class ProductExtractor {
    private static final int MAX_CANDIDATES = 256;

    /* 仅保存轮询位置，不缓存资源数量；大库存分段访问，避免后部候选饥饿。 */
    public static final class Cursor {
        private final Map<AEKeyType, Integer> offsets = new HashMap<>();
        private int source;
    }

    /**
     * @param sources         按资源类型索引的来源存储
     * @param destination     产物回传库存
     * @param maxOperations   本次最多提取的操作数
     * @param maxEntries      本次最多处理的不同产物条目数
     * @param actionSource    AE 操作来源
     * @param allowed         产物过滤器，例如输出端的严格返回规则
     * @param overflowHandler 来源与目标模拟结果不一致、且无法退回来源时的恢复处理器
     */
    public static Result extract(
            Map<AEKeyType, MEStorage> sources, GenericInternalInventory destination,
            int maxOperations, int maxEntries, IActionSource actionSource,
            Predicate<AEKey> allowed, Consumer<GenericStack> overflowHandler
    ) {
        return ProductExtractor.extract(sources, destination, maxOperations, maxEntries, actionSource,
                allowed, overflowHandler, null);
    }

    /* 精确返回按声明资源直接提取，模糊及无阻挡模式仍枚举真实库存。 */
    public static Result extract(
            Map<AEKeyType, MEStorage> sources, GenericInternalInventory destination,
            int maxOperations, int maxEntries, IActionSource actionSource,
            Predicate<AEKey> allowed, Consumer<GenericStack> overflowHandler, @Nullable Set<AEKey> exactKeys
    ) {
        return ProductExtractor.extract(sources, destination, maxOperations, maxEntries, actionSource,
                allowed, overflowHandler, exactKeys, new Cursor());
    }

    /* 预算限制过滤和能力访问；策略自身枚举库存的成本仍由外部实现决定。 */
    public static Result extract(
            Map<AEKeyType, MEStorage> sources, GenericInternalInventory destination,
            int maxOperations, int maxEntries, IActionSource actionSource,
            Predicate<AEKey> allowed, Consumer<GenericStack> overflowHandler,
            @Nullable Set<AEKey> exactKeys, Cursor cursor
    ) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(actionSource, "actionSource");
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(overflowHandler, "overflowHandler");

        if (maxOperations <= 0 || maxEntries <= 0 || !destination.canInsert()) {
            return new Result(0, 0, false);
        }

        int movedOperations = 0;
        int processedEntries = 0;
        long insertedTotal = 0;
        long lastFlushedTotal = -1;
        boolean destinationBlocked = false;
        Map<MEStorage, KeyCounter> snapshots = new IdentityHashMap<>();
        List<AEKey> declared = exactKeys == null ? List.of() : new ArrayList<>(exactKeys);
        List<Map.Entry<AEKeyType, MEStorage>> sourceList = new ArrayList<>(sources.entrySet());
        WorkBudget budget = new WorkBudget(ProductExtractor.MAX_CANDIDATES);
        int startSource = sourceList.isEmpty() ? 0 : Math.floorMod(cursor.source, sourceList.size());

        destination.beginBatch();
        try {
            for (int sourceOffset = 0; sourceOffset < sourceList.size() && budget.remaining() > 0
                    && movedOperations < maxOperations && processedEntries < maxEntries; sourceOffset++) {
                int sourceIndex = (startSource + sourceOffset) % sourceList.size();
                cursor.source = (sourceIndex + 1) % sourceList.size();
                Map.Entry<AEKeyType, MEStorage> sourceEntry = sourceList.get(sourceIndex);
                MEStorage source = sourceEntry.getValue();
                // 只在本轮复用接口返回的独立快照，不再为所有条目复制 GenericStack。
                KeyCounter available = exactKeys == null
                        ? snapshots.computeIfAbsent(source, MEStorage::getAvailableStacks) : null;
                int size = available == null ? declared.size() : available.size();
                int start = size == 0 ? 0
                        : Math.floorMod(cursor.offsets.getOrDefault(sourceEntry.getKey(), 0), size);
                Iterator<Object2LongMap.Entry<AEKey>> entries = available == null
                        ? Collections.emptyIterator() : available.iterator();
                for (int skipped = 0; skipped < start && entries.hasNext(); skipped++) {
                    entries.next();
                }
                for (int visited = 0; visited < size
                        && movedOperations < maxOperations && processedEntries < maxEntries
                        && budget.remaining() > 0; visited++) {
                    int index = (start + visited) % size;
                    cursor.offsets.put(sourceEntry.getKey(), (index + 1) % size);
                    AEKey key;
                    long availableAmount;
                    if (available == null) {
                        key = declared.get(index);
                        availableAmount = Long.MAX_VALUE;
                    } else {
                        if (!entries.hasNext()) {
                            entries = available.iterator();
                        }
                        Object2LongMap.Entry<AEKey> entry = entries.next();
                        key = entry.getKey();
                        availableAmount = entry.getLongValue();
                    }
                    if (availableAmount <= 0) {
                        continue;
                    }
                    if (!budget.tryUse() || key.getType() != sourceEntry.getKey() || !allowed.test(key)) {
                        continue;
                    }

                    long amountPerOperation = Math.max(1L, key.getAmountPerOperation());
                    long remainingOperations = maxOperations - movedOperations;
                    long operationLimit = Utils.saturatingMultiply(remainingOperations, amountPerOperation);
                    long requested = Math.min(availableAmount, operationLimit);
                    if (exactKeys != null) {
                        requested = Math.clamp(source.extract(
                                key, requested, Actionable.SIMULATE, actionSource), 0, requested);
                        if (requested == 0) {
                            continue;
                        }
                    }

                    long accepted = ProductExtractor.insert(destination, key, requested, Actionable.SIMULATE);
                    if (accepted <= 0 && insertedTotal != lastFlushedTotal
                            && destination instanceof ReceiveInventory receiver) {
                        receiver.flushSettled();
                        lastFlushedTotal = insertedTotal;
                        accepted = ProductExtractor.insert(destination, key, requested, Actionable.SIMULATE);
                    }
                    if (accepted <= 0) {
                        destinationBlocked = true;
                        continue;
                    }

                    long extracted = Math.max(0, source.extract(
                            key, Math.min(requested, accepted), Actionable.MODULATE, actionSource));
                    if (extracted <= 0) {
                        continue;
                    }

                    long inserted = ProductExtractor.insert(destination, key, extracted, Actionable.MODULATE);
                    inserted = Math.clamp(inserted, 0, extracted);
                    long remainder = extracted - inserted;
                    if (remainder > 0) {
                        long restored = Math.clamp(
                                source.insert(key, remainder, Actionable.MODULATE, actionSource), 0, remainder);
                        long unrecovered = remainder - restored;
                        if (unrecovered > 0) {
                            overflowHandler.accept(new GenericStack(key, unrecovered));
                        }
                    }
                    if (inserted > 0) {
                        insertedTotal = Utils.saturatingAdd(insertedTotal, inserted);
                        movedOperations = (int) Math.min(maxOperations,
                                (long) movedOperations + Utils.operationsFor(inserted, amountPerOperation));
                        processedEntries++;
                    }
                }
            }
        } finally {
            destination.endBatch();
        }

        return new Result(movedOperations, insertedTotal, destinationBlocked);
    }

    private static long insert(GenericInternalInventory destination, AEKey key, long amount, Actionable mode) {
        if (destination instanceof ReceiveInventory receiver) {
            return receiver.insertExtracted(key, amount, mode);
        }
        long remaining = amount;
        long insertedTotal = 0;

        for (int slot = 0; slot < destination.size() && remaining > 0; slot++) {
            if (!destination.isAllowedIn(slot, key)) {
                continue;
            }

            long inserted = Math.max(0, destination.insert(slot, key, remaining, mode));
            inserted = Math.min(inserted, remaining);
            insertedTotal += inserted;
            remaining -= inserted;
        }

        return insertedTotal;
    }

    public record Result(int movedOperations, long insertedAmount, boolean destinationBlocked) {
    }
}
