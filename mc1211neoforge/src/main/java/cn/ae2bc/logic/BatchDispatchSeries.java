package cn.ae2bc.logic;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/** Persistent identity for consecutive, exactly equivalent provider-owned batch tasks. */
final class BatchDispatchSeries {
    private static final String SESSION_ID = "SessionId";
    private static final String PATTERN = "Pattern";
    private static final String CONFIGURED_BATCH_COUNT = "ConfiguredBatchCount";
    private static final String TASK_UNITS = "TaskUnits";
    private static final String ATOMIC_INPUTS = "AtomicInputs";
    private static final String STACKS = "Stacks";

    private final UUID sessionId;
    private final AEItemKey patternDefinition;
    private final long configuredBatchCount;
    private final long taskUnits;
    private final KeyCounter[] atomicInputs;

    private BatchDispatchSeries(UUID sessionId, AEItemKey patternDefinition, long configuredBatchCount,
                                long taskUnits, KeyCounter[] atomicInputs) {
        this.sessionId = sessionId;
        this.patternDefinition = patternDefinition;
        this.configuredBatchCount = configuredBatchCount;
        this.taskUnits = taskUnits;
        this.atomicInputs = copyCounters(atomicInputs);
    }

    static BatchDispatchSeries from(BatchDispatchContext context) {
        return new BatchDispatchSeries(context.sessionId(), context.patternDefinition(),
                context.configuredBatchCount(), context.taskUnits(), context.atomicInputs());
    }

    static BatchDispatchSeries read(CompoundTag tag, HolderLookup.Provider registries) {
        AEItemKey pattern = tag.contains(PATTERN, Tag.TAG_COMPOUND)
                ? AEItemKey.fromTag(registries, tag.getCompound(PATTERN)) : null;
        UUID sessionId = tag.hasUUID(SESSION_ID) ? tag.getUUID(SESSION_ID) : null;
        long configuredBatchCount = tag.getLong(CONFIGURED_BATCH_COUNT);
        long taskUnits = tag.getLong(TASK_UNITS);
        KeyCounter[] atomicInputs = readCounters(tag.getList(ATOMIC_INPUTS, Tag.TAG_COMPOUND), registries);
        if (sessionId == null || pattern == null || configuredBatchCount <= 1
                || taskUnits <= 0 || atomicInputs.length == 0) {
            return null;
        }
        return new BatchDispatchSeries(sessionId, pattern, configuredBatchCount, taskUnits, atomicInputs);
    }

    CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(SESSION_ID, sessionId);
        tag.put(PATTERN, patternDefinition.toTag(registries));
        tag.putLong(CONFIGURED_BATCH_COUNT, configuredBatchCount);
        tag.putLong(TASK_UNITS, taskUnits);
        tag.put(ATOMIC_INPUTS, writeCounters(atomicInputs, registries));
        return tag;
    }

    UUID sessionId() {
        return sessionId;
    }

    boolean matches(BatchDispatchContext context) {
        return Objects.equals(patternDefinition, context.patternDefinition())
                && configuredBatchCount == context.configuredBatchCount()
                && taskUnits == context.taskUnits()
                && countersEqual(atomicInputs, context.atomicInputs());
    }

    private static boolean countersEqual(KeyCounter[] left, KeyCounter[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!counterEqual(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean counterEqual(KeyCounter left, KeyCounter right) {
        for (var entry : left) {
            if (entry.getLongValue() > 0 && right.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        for (var entry : right) {
            if (entry.getLongValue() > 0 && left.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private static KeyCounter[] copyCounters(KeyCounter[] source) {
        KeyCounter[] result = new KeyCounter[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = new KeyCounter();
            for (var entry : source[i]) {
                if (entry.getLongValue() > 0) {
                    result[i].add(entry.getKey(), entry.getLongValue());
                }
            }
        }
        return result;
    }

    private static ListTag writeCounters(KeyCounter[] counters, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (KeyCounter counter : counters) {
            CompoundTag holder = new CompoundTag();
            ListTag stacks = new ListTag();
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    stacks.add(GenericStack.writeTag(registries,
                            new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
            holder.put(STACKS, stacks);
            result.add(holder);
        }
        return result;
    }

    private static KeyCounter[] readCounters(ListTag list, HolderLookup.Provider registries) {
        var result = new ArrayList<KeyCounter>(list.size());
        for (int i = 0; i < list.size(); i++) {
            KeyCounter counter = new KeyCounter();
            for (Tag raw : list.getCompound(i).getList(STACKS, Tag.TAG_COMPOUND)) {
                GenericStack stack = GenericStack.readTag(registries, (CompoundTag) raw);
                if (stack != null && stack.amount() > 0) {
                    counter.add(stack.what(), stack.amount());
                }
            }
            result.add(counter);
        }
        return result.toArray(KeyCounter[]::new);
    }
}
