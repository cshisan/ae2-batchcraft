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

/** Strictly maps authoritative runtime inputs back to the encoded processing-pattern slots. */
final class ProcessingInputMapper {
    private ProcessingInputMapper() {
    }

    static @Nullable List<SlotInput> map(AEProcessingPattern pattern, KeyCounter[] runtimeInputs,
                                         Level level, long divisor, long units) {
        if (pattern == null || runtimeInputs == null || level == null || divisor <= 0 || units <= 0) {
            return null;
        }
        try {
            List<RuntimeInput> runtime = flatten(runtimeInputs);
            if (runtime.isEmpty()) {
                return null;
            }

            var sparse = pattern.getSparseInputs();
            long[] required = new long[sparse.size()];
            IPatternDetails.IInput[] matchers = new IPatternDetails.IInput[sparse.size()];
            IPatternDetails.IInput[] denseInputs = pattern.getInputs();
            int denseIndex = 0;
            long nominalTotal = 0;
            for (int slot = 0; slot < sparse.size(); slot++) {
                GenericStack encoded = sparse.get(slot);
                if (encoded == null || encoded.amount() <= 0) {
                    continue;
                }
                if (denseIndex >= denseInputs.length || encoded.amount() % divisor != 0) {
                    return null;
                }
                long amount = Math.multiplyExact(encoded.amount() / divisor, units);
                if (amount <= 0) {
                    return null;
                }
                required[slot] = amount;
                matchers[slot] = denseInputs[denseIndex++];
                nominalTotal = Math.addExact(nominalTotal, amount);
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
            if (runtimeTotal % nominalTotal != 0) {
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
