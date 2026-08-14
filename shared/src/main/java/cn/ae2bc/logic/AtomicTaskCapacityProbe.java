package cn.ae2bc.logic;

import java.util.function.LongPredicate;

/** Finds a maximum accepted atomic-task count using exponential growth followed by binary search. */
public final class AtomicTaskCapacityProbe {
    private AtomicTaskCapacityProbe() {
    }

    public static long findMaximum(long upperBound, LongPredicate accepts) {
        if (upperBound <= 0 || !accepts.test(1)) {
            return 0;
        }
        if (upperBound == 1) {
            return 1;
        }

        long accepted = 1;
        while (accepted < upperBound) {
            long candidate = accepted > upperBound / 2 ? upperBound : accepted * 2;
            if (!accepts.test(candidate)) {
                return binarySearch(accepted, candidate - 1, accepts);
            }
            accepted = candidate;
        }
        return accepted;
    }

    private static long binarySearch(long accepted, long upperBound, LongPredicate accepts) {
        long low = accepted;
        long high = upperBound;
        while (low < high) {
            long middle = low + (high - low + 1) / 2;
            if (accepts.test(middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }
}
