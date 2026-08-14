package cn.ae2bc.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

public final class PatternInputSlotAllocatorTest {
    @Test
    public void splitsCondensedRuntimeStackAcrossPatternSlots() {
        List<PatternInputSlotAllocator.Allocation> result = PatternInputSlotAllocator.allocate(
                new long[] { 1, 1 }, new long[] { 2 }, (patternSlot, runtimeSlot) -> true);

        assertEquals(2, result.size());
        assertAllocation(result.get(0), 0, 0, 1);
        assertAllocation(result.get(1), 1, 0, 1);
    }

    @Test
    public void combinesRuntimeStacksForOnePatternSlot() {
        List<PatternInputSlotAllocator.Allocation> result = PatternInputSlotAllocator.allocate(
                new long[] { 3 }, new long[] { 1, 2 }, (patternSlot, runtimeSlot) -> true);

        assertEquals(2, result.size());
        assertAllocation(result.get(0), 0, 0, 1);
        assertAllocation(result.get(1), 0, 1, 2);
    }

    @Test
    public void usesPatternThenRuntimeSlotOrder() {
        List<PatternInputSlotAllocator.Allocation> result = PatternInputSlotAllocator.allocate(
                new long[] { 1, 1 }, new long[] { 1, 1 },
                (patternSlot, runtimeSlot) -> patternSlot != runtimeSlot);

        assertEquals(2, result.size());
        assertAllocation(result.get(0), 0, 1, 1);
        assertAllocation(result.get(1), 1, 0, 1);
    }

    @Test
    public void preservesSparseSlotsAndRepeatedMaterialAmounts() {
        int[] patternTypes = { -1, 7, -1, 7, 9 };
        int[] runtimeTypes = { 7, 9, 7 };
        List<PatternInputSlotAllocator.Allocation> result = PatternInputSlotAllocator.allocate(
                new long[] { 0, 2, 0, 1, 1 }, new long[] { 2, 1, 1 },
                (patternSlot, runtimeSlot) -> patternTypes[patternSlot] == runtimeTypes[runtimeSlot]);

        assertEquals(3, result.size());
        assertAllocation(result.get(0), 1, 0, 2);
        assertAllocation(result.get(1), 3, 2, 1);
        assertAllocation(result.get(2), 4, 1, 1);
    }

    @Test
    public void rejectsMissingOrUnmatchedAmounts() {
        assertNull(PatternInputSlotAllocator.allocate(
                new long[] { 2 }, new long[] { 1 }, (patternSlot, runtimeSlot) -> true));
        assertNull(PatternInputSlotAllocator.allocate(
                new long[] { 1 }, new long[] { 2 }, (patternSlot, runtimeSlot) -> true));
        assertNull(PatternInputSlotAllocator.allocate(
                new long[] { 1 }, new long[] { 1 }, (patternSlot, runtimeSlot) -> false));

        assertNull(PatternInputSlotAllocator.allocate(
                new long[] { 1 }, new long[] { -1 }, (patternSlot, runtimeSlot) -> true));
    }

    @Test
    public void supportsAmountsAboveIntegerRange() {
        long amount = (long) Integer.MAX_VALUE + 10;
        List<PatternInputSlotAllocator.Allocation> result = PatternInputSlotAllocator.allocate(
                new long[] { amount }, new long[] { amount }, (patternSlot, runtimeSlot) -> true);

        assertEquals(1, result.size());
        assertAllocation(result.get(0), 0, 0, amount);
    }

    private static void assertAllocation(PatternInputSlotAllocator.Allocation allocation,
            int patternSlot, int runtimeSlot, long amount) {
        assertEquals(patternSlot, allocation.getPatternSlot());
        assertEquals(runtimeSlot, allocation.getRuntimeSlot());
        assertEquals(amount, allocation.getAmount());
    }
}
