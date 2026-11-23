package org.ae2bc.core.dispatch;

/* 纯数量分配；均分只遍历容量，不重复调用外部库存。 */
public final class ResourceDistribution {
    private ResourceDistribution() {
    }

    public static long[] allocate(long amount, long[] capacities, boolean evenly) {
        long[] allocated = new long[capacities.length];
        long remaining = Math.max(0, amount);
        if (!evenly) {
            for (int index = 0; index < capacities.length && remaining > 0; index++) {
                allocated[index] = Math.min(remaining, Math.max(0, capacities[index]));
                remaining -= allocated[index];
            }
            return allocated;
        }
        // 每轮至少耗尽一个目标容量，或分完余量；循环次数不依赖资源数量。
        while (remaining > 0) {
            int active = 0;
            for (int index = 0; index < capacities.length; index++) {
                if (capacities[index] > allocated[index]) {
                    active++;
                }
            }
            if (active == 0) {
                break;
            }
            long share = remaining / active;
            long extra = remaining % active;
            for (int index = 0; index < capacities.length; index++) {
                long capacity = capacities[index] - allocated[index];
                if (capacity <= 0) {
                    continue;
                }
                long requested = share;
                if (extra > 0) {
                    requested++;
                    extra--;
                }
                long assigned = Math.min(capacity, requested);
                allocated[index] += assigned;
                remaining -= assigned;
            }
        }
        return allocated;
    }
}
