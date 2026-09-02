package cn.ae2bc.logic;

import cn.ae2bc.core.dispatch.TaskAllocationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TaskAllocationPlannerTest {
    @Test
    void roundRobinRetainsTheExistingFairDistribution() {
        assertArrayEquals(new long[]{3, 4, 3}, TaskAllocationPlanner.distribute(
                10, new long[]{10, 10, 10}, 1, TaskAllocationMode.ROUND_ROBIN, null));
    }

    @Test
    void randomSelectsOnePositiveCapacityWithoutRemovingIt() {
        long[] capacities = {0, 8, 0, 12};
        assertArrayEquals(new long[]{0, 0, 0, 10}, TaskAllocationPlanner.distribute(
                10, capacities, 0, TaskAllocationMode.RANDOM, bound -> 1));
        assertArrayEquals(new long[]{0, 0, 0, 10}, TaskAllocationPlanner.distribute(
                10, capacities, 0, TaskAllocationMode.RANDOM, bound -> 1));
        assertArrayEquals(new long[]{0, 0, 0, 0}, TaskAllocationPlanner.distribute(
                Long.MAX_VALUE, new long[]{0, 0, 0, 0}, 0, TaskAllocationMode.RANDOM, bound -> 0));
    }

    @Test
    void priorityFillsEarlierCapacitiesFirst() {
        assertArrayEquals(new long[]{3, 5, 2}, TaskAllocationPlanner.distribute(
                10, new long[]{3, 5, 7}, 2, TaskAllocationMode.PRIORITY, null));
    }
}
