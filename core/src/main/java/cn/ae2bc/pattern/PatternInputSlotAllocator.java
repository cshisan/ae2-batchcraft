package cn.ae2bc.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministically maps runtime crafting inputs back to sparse pattern slots. */
public final class PatternInputSlotAllocator {
    private PatternInputSlotAllocator() {
    }

    public static List<Allocation> allocate(long[] requiredAmounts, int[] availableAmounts,
            SlotMatcher matcher) {
        if (requiredAmounts == null || availableAmounts == null || matcher == null) {
            return null;
        }
        long[] remainingRequired = requiredAmounts.clone();
        int[] remainingAvailable = availableAmounts.clone();
        List<Allocation> result = new ArrayList<Allocation>();

        for (int patternSlot = 0; patternSlot < remainingRequired.length; patternSlot++) {
            long required = remainingRequired[patternSlot];
            if (required < 0) {
                return null;
            }
            for (int runtimeSlot = 0;
                    required > 0 && runtimeSlot < remainingAvailable.length; runtimeSlot++) {
                int available = remainingAvailable[runtimeSlot];
                if (available <= 0 || !matcher.matches(patternSlot, runtimeSlot)) {
                    continue;
                }
                int allocated = (int) Math.min(required, (long) available);
                result.add(new Allocation(patternSlot, runtimeSlot, allocated));
                required -= allocated;
                remainingAvailable[runtimeSlot] -= allocated;
            }
            if (required != 0) {
                return null;
            }
        }

        for (int available : remainingAvailable) {
            if (available != 0) {
                return null;
            }
        }
        return Collections.unmodifiableList(result);
    }

    public interface SlotMatcher {
        boolean matches(int patternSlot, int runtimeSlot);
    }

    public static final class Allocation {
        private final int patternSlot;
        private final int runtimeSlot;
        private final int amount;

        private Allocation(int patternSlot, int runtimeSlot, int amount) {
            this.patternSlot = patternSlot;
            this.runtimeSlot = runtimeSlot;
            this.amount = amount;
        }

        public int getPatternSlot() {
            return patternSlot;
        }

        public int getRuntimeSlot() {
            return runtimeSlot;
        }

        public int getAmount() {
            return amount;
        }
    }
}
