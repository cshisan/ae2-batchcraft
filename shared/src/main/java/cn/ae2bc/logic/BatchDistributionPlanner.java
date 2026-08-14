package cn.ae2bc.logic;

/** Fairly distributes atomic task units while respecting each endpoint's capacity. */
public final class BatchDistributionPlanner {
    private BatchDistributionPlanner() {
    }

    public static long[] distribute(long totalUnits, long[] capacities, int startIndex) {
        if (totalUnits <= 0 || capacities == null || capacities.length == 0) {
            return new long[capacities == null ? 0 : capacities.length];
        }
        long[] allocated = new long[capacities.length];
        long totalCapacity = 0;
        for (long capacity : capacities) {
            totalCapacity = saturatingAdd(totalCapacity, Math.max(0, capacity));
        }
        long remaining = Math.min(totalUnits, totalCapacity);
        if (remaining <= 0) {
            return allocated;
        }
        int cursor = Math.floorMod(startIndex, capacities.length);
        while (remaining > 0) {
            int active = 0;
            for (int i = 0; i < capacities.length; i++) {
                if (allocated[i] < Math.max(0, capacities[i])) {
                    active++;
                }
            }
            if (active == 0) {
                return allocated;
            }
            long roundRemaining = remaining;
            long base = roundRemaining / active;
            long extra = roundRemaining % active;
            int activeIndex = 0;
            long before = remaining;
            for (int offset = 0; offset < capacities.length && remaining > 0; offset++) {
                int index = (cursor + offset) % capacities.length;
                long available = Math.max(0, capacities[index]) - allocated[index];
                if (available <= 0) {
                    continue;
                }
                long target = base + (activeIndex < extra ? 1 : 0);
                activeIndex++;
                long amount = Math.min(remaining, Math.min(target, available));
                allocated[index] += amount;
                remaining -= amount;
            }
            if (remaining == before) {
                return allocated;
            }
            cursor = (cursor + 1) % capacities.length;
        }
        return allocated;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
