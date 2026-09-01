package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.ae2bc.pattern.PatternInputSlotAllocator;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Strictly maps runtime processing inputs back to encoded sparse pattern slots. */
final class ProcessingInputMapper {
    private ProcessingInputMapper() {
    }

    static @Nullable List<SlotInput> map(AEProcessingPattern pattern, KeyCounter[] runtimeInputs,
                                         Level level) {
        if (pattern == null || runtimeInputs == null || level == null) {
            return null;
        }
        try {
            List<RuntimeInput> runtime = flatten(runtimeInputs);
            if (runtime.isEmpty()) {
                return null;
            }

            GenericStack[] sparse = pattern.getSparseInputs();
            IPatternDetails.IInput[] denseInputs = pattern.getInputs();
            long[] required = new long[sparse.length];
            IPatternDetails.IInput[] matchers = new IPatternDetails.IInput[sparse.length];
            int denseIndex = 0;
            long nominalTotal = 0;
            for (int slot = 0; slot < sparse.length; slot++) {
                GenericStack encoded = sparse[slot];
                if (encoded == null || encoded.amount() <= 0) {
                    continue;
                }
                if (denseIndex >= denseInputs.length) {
                    return null;
                }
                required[slot] = encoded.amount();
                matchers[slot] = denseInputs[denseIndex++];
                nominalTotal = Math.addExact(nominalTotal, encoded.amount());
            }
            if (denseIndex != denseInputs.length || nominalTotal <= 0) {
                return null;
            }

            long runtimeTotal = 0;
            long[] available = new long[runtime.size()];
            for (int i = 0; i < runtime.size(); i++) {
                available[i] = runtime.get(i).amount();
                runtimeTotal = Math.addExact(runtimeTotal, available[i]);
            }
            if (runtimeTotal <= 0 || runtimeTotal % nominalTotal != 0) {
                return null;
            }
            long runtimeScale = runtimeTotal / nominalTotal;
            if (runtimeScale <= 0) {
                return null;
            }
            for (int slot = 0; slot < required.length; slot++) {
                required[slot] = Math.multiplyExact(required[slot], runtimeScale);
            }

            List<PatternInputSlotAllocator.Allocation> allocations = PatternInputSlotAllocator.allocate(
                    required, available,
                    (patternSlot, runtimeSlot) -> matchers[patternSlot] != null
                            && matchers[patternSlot].isValid(runtime.get(runtimeSlot).what(), level));
            if (allocations == null) {
                return null;
            }
            List<SlotInput> result = new ArrayList<>(allocations.size());
            for (var allocation : allocations) {
                RuntimeInput input = runtime.get(allocation.getRuntimeSlot());
                result.add(new SlotInput(allocation.getPatternSlot(),
                        new GenericStack(input.what(), allocation.getAmount())));
            }
            return result;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<RuntimeInput> flatten(KeyCounter[] runtimeInputs) {
        List<RuntimeInput> result = new ArrayList<>();
        for (KeyCounter holder : runtimeInputs) {
            if (holder == null) {
                continue;
            }
            for (var entry : holder) {
                if (entry.getLongValue() > 0) {
                    result.add(new RuntimeInput(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return result;
    }

    record SlotInput(int slot, GenericStack stack) {
    }

    private record RuntimeInput(AEKey what, long amount) {
    }
}
