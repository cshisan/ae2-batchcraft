package cn.ae2bc.logic;

import cn.ae2bc.core.dispatch.TaskAllocationMode;

import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Plans atomic task units according to the selected endpoint allocation mode. */
public final class TaskAllocationPlanner {
    private TaskAllocationPlanner() {
    }

    public static long[] distribute(long totalUnits, long[] capacities, int startIndex,
                                    TaskAllocationMode mode, IntUnaryOperator boundedRandom) {
        Objects.requireNonNull(mode, "mode");
        if (capacities == null || capacities.length == 0 || totalUnits <= 0) {
            return new long[capacities == null ? 0 : capacities.length];
        }
        switch (mode) {
            case ROUND_ROBIN:
                return BatchDistributionPlanner.distribute(totalUnits, capacities, startIndex);
            case RANDOM:
                return distributeRandom(totalUnits, capacities, boundedRandom);
            case PRIORITY:
                return distributePriority(totalUnits, capacities);
            default:
                throw new IllegalStateException("Unhandled task allocation mode: " + mode);
        }
    }

    private static long[] distributeRandom(long totalUnits, long[] capacities,
                                           IntUnaryOperator boundedRandom) {
        Objects.requireNonNull(boundedRandom, "boundedRandom");
        long[] allocated = new long[capacities.length];
        int availableCount = 0;
        for (long capacity : capacities) {
            if (capacity > 0) {
                availableCount++;
            }
        }
        if (availableCount == 0) {
            return allocated;
        }
        int selectedAvailableIndex = boundedRandom.applyAsInt(availableCount);
        if (selectedAvailableIndex < 0 || selectedAvailableIndex >= availableCount) {
            throw new IllegalArgumentException("random index outside bound: " + selectedAvailableIndex);
        }
        for (int index = 0; index < capacities.length; index++) {
            if (capacities[index] <= 0) {
                continue;
            }
            if (selectedAvailableIndex-- == 0) {
                allocated[index] = Math.min(totalUnits, capacities[index]);
                return allocated;
            }
        }
        throw new IllegalStateException("random endpoint selection failed");
    }

    private static long[] distributePriority(long totalUnits, long[] capacities) {
        long[] allocated = new long[capacities.length];
        long remaining = totalUnits;
        for (int index = 0; index < capacities.length && remaining > 0; index++) {
            long amount = Math.min(remaining, Math.max(0, capacities[index]));
            allocated[index] = amount;
            remaining -= amount;
        }
        return allocated;
    }
}
