package cn.ae2bc.logic;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent state for one provider-owned task that is admitted one capacity-limited round at a time. */
final class BatchDispatchContext {
    private static final String PATTERN = "Pattern";
    private static final String SESSION_ID = "SessionId";
    private static final String CONFIGURED_BATCH_COUNT = "ConfiguredBatchCount";
    private static final String TASK_UNITS = "TaskUnits";
    private static final String REMAINING_UNITS = "RemainingUnits";
    private static final String ROUND_UNITS = "RoundUnits";
    private static final String ATOMIC_INPUTS = "AtomicInputs";
    private static final String HELD_INPUTS = "HeldInputs";
    private static final String STACKS = "Stacks";
    private static final String ROUND_ALLOCATIONS = "RoundAllocations";
    private static final String ENDPOINT_ID = "EndpointId";
    private static final String UNITS = "Units";

    private final AEItemKey patternDefinition;
    private final UUID sessionId;
    private final long configuredBatchCount;
    private final long taskUnits;
    private final KeyCounter[] atomicInputs;
    private final KeyCounter[] heldInputs;
    private long remainingUnits;
    private long roundUnits;
    private final Map<String, Long> roundAllocations;
    private transient IPatternDetails decodedPattern;
    private transient boolean patternDecoded;
    private transient PatternDispatchMetadata cachedMetadata;

    private BatchDispatchContext(AEItemKey patternDefinition, UUID sessionId, long configuredBatchCount,
                                 long taskUnits,
                                 KeyCounter[] atomicInputs, KeyCounter[] heldInputs,
                                 long remainingUnits, long roundUnits,
                                 Map<String, Long> roundAllocations) {
        this.patternDefinition = patternDefinition;
        this.sessionId = sessionId;
        this.configuredBatchCount = configuredBatchCount;
        this.taskUnits = taskUnits;
        this.atomicInputs = atomicInputs;
        this.heldInputs = heldInputs;
        this.remainingUnits = remainingUnits;
        this.roundUnits = roundUnits;
        this.roundAllocations = roundAllocations;
    }

    static BatchDispatchContext create(IPatternDetails taskPattern, KeyCounter[] taskInputs, Level level) {
        return create(taskPattern, taskInputs, level, UUID.randomUUID());
    }

    static BatchDispatchContext create(IPatternDetails taskPattern, KeyCounter[] taskInputs, Level level,
                                       UUID sessionId) {
        return create(taskPattern, taskInputs, level, sessionId,
                PatternDispatchMetadata.create(taskPattern, level));
    }

    static BatchDispatchContext create(IPatternDetails taskPattern, KeyCounter[] taskInputs, Level level,
                                       UUID sessionId, PatternDispatchMetadata metadata) {
        var encodedPattern = metadata.processingPattern();
        if (encodedPattern == null) {
            return null;
        }
        long configuredBatchCount = metadata.batchCount();
        if (configuredBatchCount <= 1 || taskInputs == null
                || taskInputs.length != encodedPattern.getInputs().length) {
            return null;
        }

        long taskUnits = -1;
        for (int i = 0; i < taskInputs.length; i++) {
            long encodedAmount = encodedPattern.getInputs()[i].getMultiplier();
            if (encodedAmount <= 0 || encodedAmount % configuredBatchCount != 0) {
                return null;
            }
            long atomicAmount = encodedAmount / configuredBatchCount;
            long suppliedAmount = total(taskInputs[i]);
            if (suppliedAmount <= 0 || suppliedAmount % atomicAmount != 0) {
                return null;
            }
            long holderUnits = suppliedAmount / atomicAmount;
            if (taskUnits < 0) {
                taskUnits = holderUnits;
            } else if (taskUnits != holderUnits) {
                return null;
            }
        }
        if (taskUnits <= 0) {
            return null;
        }
        KeyCounter[] atomicInputs = PatternInputScaling.atomicInputs(taskInputs, taskUnits);
        if (atomicInputs == null) {
            return null;
        }
        PatternDispatchMetadata atomicMetadata = metadata.forAtomicUnits(1);
        if (!atomicMetadata.isValid()) {
            return null;
        }
        return new BatchDispatchContext(encodedPattern.getDefinition(), sessionId, configuredBatchCount, taskUnits,
                atomicInputs, emptyCounters(atomicInputs.length), taskUnits, 0,
                new LinkedHashMap<>());
    }

    static BatchDispatchContext read(CompoundTag tag, HolderLookup.Provider registries) {
        AEItemKey definition = tag.contains(PATTERN, Tag.TAG_COMPOUND)
                ? AEItemKey.fromTag(registries, tag.getCompound(PATTERN)) : null;
        UUID sessionId = tag.hasUUID(SESSION_ID) ? tag.getUUID(SESSION_ID) : UUID.randomUUID();
        long configuredBatchCount = tag.getLong(CONFIGURED_BATCH_COUNT);
        long remainingUnits = tag.getLong(REMAINING_UNITS);
        long taskUnits = tag.contains(TASK_UNITS) ? tag.getLong(TASK_UNITS) : remainingUnits;
        long roundUnits = tag.getLong(ROUND_UNITS);
        KeyCounter[] atomicInputs = readCounters(tag.getList(ATOMIC_INPUTS, Tag.TAG_COMPOUND), registries);
        KeyCounter[] heldInputs = readCounters(tag.getList(HELD_INPUTS, Tag.TAG_COMPOUND), registries);
        Map<String, Long> allocations = readAllocations(tag.getList(ROUND_ALLOCATIONS, Tag.TAG_COMPOUND));
        if (definition == null || configuredBatchCount <= 1 || taskUnits <= 0 || remainingUnits <= 0 || roundUnits < 0
                || atomicInputs.length == 0 || heldInputs.length != atomicInputs.length) {
            return null;
        }
        var context = new BatchDispatchContext(definition, sessionId, configuredBatchCount, taskUnits,
                atomicInputs, heldInputs, remainingUnits, roundUnits, allocations);
        return context.isStateValid() ? context : null;
    }

    CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(PATTERN, patternDefinition.toTag(registries));
        tag.putUUID(SESSION_ID, sessionId);
        tag.putLong(CONFIGURED_BATCH_COUNT, configuredBatchCount);
        tag.putLong(TASK_UNITS, taskUnits);
        tag.putLong(REMAINING_UNITS, remainingUnits);
        tag.putLong(ROUND_UNITS, roundUnits);
        tag.put(ATOMIC_INPUTS, writeCounters(atomicInputs, registries));
        tag.put(HELD_INPUTS, writeCounters(heldInputs, registries));
        tag.put(ROUND_ALLOCATIONS, writeAllocations());
        return tag;
    }

    IPatternDetails decodePattern(Level level) {
        if (!patternDecoded) {
            decodedPattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(patternDefinition, level);
            patternDecoded = true;
        }
        return decodedPattern;
    }

    PatternDispatchMetadata dispatchMetadata(Level level) {
        if (cachedMetadata == null) {
            IPatternDetails pattern = decodePattern(level);
            cachedMetadata = pattern == null ? null : PatternDispatchMetadata.create(pattern, level);
        }
        return cachedMetadata;
    }

    KeyCounter[] atomicInputs() {
        return atomicInputs;
    }

    UUID sessionId() {
        return sessionId;
    }

    AEItemKey patternDefinition() {
        return patternDefinition;
    }

    long configuredBatchCount() {
        return configuredBatchCount;
    }

    long taskUnits() {
        return taskUnits;
    }

    long remainingUnits() {
        return remainingUnits;
    }

    long roundUnits() {
        return roundUnits;
    }

    boolean isRoundReady() {
        if (roundUnits <= 0) {
            return false;
        }
        for (int i = 0; i < atomicInputs.length; i++) {
            for (var entry : atomicInputs[i]) {
                long target;
                try {
                    target = Math.multiplyExact(entry.getLongValue(), roundUnits);
                } catch (ArithmeticException exception) {
                    return false;
                }
                if (heldInputs[i].get(entry.getKey()) != target) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean startRound(Map<String, Long> allocations) {
        if (allocations == null || roundUnits != 0 || hasHeldInputs()) {
            return false;
        }
        Map<String, Long> normalizedAllocations = new LinkedHashMap<>();
        allocations.forEach((id, value) -> {
            if (id != null && !id.isBlank() && value != null && value > 0) {
                normalizedAllocations.merge(id, value, BatchDispatchContext::saturatingAdd);
            }
        });
        long units = normalizedAllocations.values().stream().reduce(0L, BatchDispatchContext::saturatingAdd);
        if (units <= 0 || units > remainingUnits) {
            return false;
        }
        roundUnits = units;
        roundAllocations.clear();
        roundAllocations.putAll(normalizedAllocations);
        return true;
    }

    Map<String, Long> roundAllocations() {
        return Map.copyOf(roundAllocations);
    }

    long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || roundUnits <= 0 || isRoundReady()) {
            return 0;
        }
        long accepted = Math.min(amount, missing(what));
        if (accepted <= 0 || mode == Actionable.SIMULATE) {
            return accepted;
        }
        long remaining = accepted;
        for (int i = 0; i < atomicInputs.length && remaining > 0; i++) {
            long atomic = atomicInputs[i].get(what);
            if (atomic <= 0) {
                continue;
            }
            long target = Math.multiplyExact(atomic, roundUnits);
            long holderMissing = target - heldInputs[i].get(what);
            long inserted = Math.min(remaining, Math.max(0, holderMissing));
            if (inserted > 0) {
                heldInputs[i].add(what, inserted);
                remaining -= inserted;
            }
        }
        return accepted - remaining;
    }

    void dispatched(String endpointId, long units) {
        if (!isRoundReady() || units <= 0 || units > roundUnits || units > remainingUnits) {
            throw new IllegalStateException("Invalid dispatched unit count");
        }
        long assigned = roundAllocations.getOrDefault(endpointId, 0L);
        if (units > assigned) {
            throw new IllegalStateException("Dispatched more units than assigned to endpoint");
        }
        for (int i = 0; i < atomicInputs.length; i++) {
            for (var entry : atomicInputs[i]) {
                heldInputs[i].remove(entry.getKey(), Math.multiplyExact(entry.getLongValue(), units));
            }
            heldInputs[i].removeZeros();
        }
        roundUnits -= units;
        remainingUnits -= units;
        if (units == assigned) {
            roundAllocations.remove(endpointId);
        } else {
            roundAllocations.put(endpointId, assigned - units);
        }
    }

    void finishRoundIfEmpty() {
        if (!hasHeldInputs()) {
            roundUnits = 0;
            roundAllocations.clear();
        }
    }

    boolean isComplete() {
        return remainingUnits == 0 && !hasHeldInputs();
    }

    void addHeldDrops(java.util.List<net.minecraft.world.item.ItemStack> drops, Level level,
                      net.minecraft.core.BlockPos pos) {
        for (KeyCounter holder : heldInputs) {
            for (var entry : holder) {
                entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
            }
        }
    }

    void returnHeldTo(MEStorage storage, IActionSource source) {
        if (storage == null) {
            return;
        }
        for (KeyCounter holder : heldInputs) {
            var keys = new java.util.ArrayList<AEKey>();
            for (var entry : holder) {
                keys.add(entry.getKey());
            }
            for (AEKey key : keys) {
                long amount = holder.get(key);
                long inserted = storage.insert(key, amount, Actionable.MODULATE, source);
                if (inserted > 0) {
                    holder.remove(key, Math.min(amount, inserted));
                }
            }
            holder.removeZeros();
        }
        finishRoundIfEmpty();
    }

    void clearHeld() {
        for (KeyCounter holder : heldInputs) {
            holder.reset();
        }
        roundUnits = 0;
        remainingUnits = 0;
        roundAllocations.clear();
    }

    private long missing(AEKey what) {
        long missing = 0;
        for (int i = 0; i < atomicInputs.length; i++) {
            long atomic = atomicInputs[i].get(what);
            if (atomic <= 0) {
                continue;
            }
            long target = Math.multiplyExact(atomic, roundUnits);
            missing = saturatingAdd(missing, Math.max(0, target - heldInputs[i].get(what)));
        }
        return missing;
    }

    private boolean isStateValid() {
        if (roundUnits > remainingUnits || sumAllocations() != roundUnits) {
            return false;
        }
        for (int i = 0; i < atomicInputs.length; i++) {
            for (var held : heldInputs[i]) {
                long atomic = atomicInputs[i].get(held.getKey());
                if (atomic <= 0 || held.getLongValue() < 0
                        || exceedsRoundTarget(held.getLongValue(), atomic, roundUnits)) {
                    return false;
                }
            }
        }
        return true;
    }

    private long sumAllocations() {
        long total = 0;
        for (long value : roundAllocations.values()) {
            if (value <= 0) {
                return -1;
            }
            total = saturatingAdd(total, value);
        }
        return total;
    }

    private ListTag writeAllocations() {
        ListTag result = new ListTag();
        roundAllocations.forEach((id, units) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(ENDPOINT_ID, id);
            entry.putLong(UNITS, units);
            result.add(entry);
        });
        return result;
    }

    private static Map<String, Long> readAllocations(ListTag list) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString(ENDPOINT_ID);
            long units = entry.getLong(UNITS);
            if (!id.isBlank() && units > 0) {
                result.merge(id, units, BatchDispatchContext::saturatingAdd);
            }
        }
        return result;
    }

    private static boolean exceedsRoundTarget(long held, long atomic, long units) {
        try {
            return held > Math.multiplyExact(atomic, units);
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private boolean hasHeldInputs() {
        for (KeyCounter holder : heldInputs) {
            for (var entry : holder) {
                if (entry.getLongValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long total(KeyCounter counter) {
        long total = 0;
        try {
            for (var entry : counter) {
                total = Math.addExact(total, entry.getLongValue());
            }
            return total;
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    private static KeyCounter[] emptyCounters(int count) {
        KeyCounter[] result = new KeyCounter[count];
        Arrays.setAll(result, ignored -> new KeyCounter());
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

    private static KeyCounter[] readCounters(ListTag holders, HolderLookup.Provider registries) {
        KeyCounter[] result = new KeyCounter[holders.size()];
        for (int i = 0; i < holders.size(); i++) {
            KeyCounter counter = new KeyCounter();
            ListTag stacks = holders.getCompound(i).getList(STACKS, Tag.TAG_COMPOUND);
            for (int j = 0; j < stacks.size(); j++) {
                GenericStack stack = GenericStack.readTag(registries, stacks.getCompound(j));
                if (stack != null && stack.amount() > 0) {
                    counter.add(stack.what(), stack.amount());
                }
            }
            result[i] = counter;
        }
        return result;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
