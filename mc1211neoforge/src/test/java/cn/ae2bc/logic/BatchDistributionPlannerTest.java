package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BatchDistributionPlannerTest {
    @Test
    void redistributesRemainderAfterCapacityLimitedRound() {
        assertArrayEquals(new long[]{100, 250, 325, 325},
                BatchDistributionPlanner.distribute(1000, new long[]{100, 250, 400, 500}, 0));
    }

    @Test
    void rotatesIndivisibleRemainder() {
        assertArrayEquals(new long[]{3, 4, 3},
                BatchDistributionPlanner.distribute(10, new long[]{10, 10, 10}, 1));
    }

    @Test
    void distributesOnlyCurrentlyAvailableCapacity() {
        assertArrayEquals(new long[]{3, 6},
                BatchDistributionPlanner.distribute(10, new long[]{3, 6}, 0));
    }

    @Test
    void returnsZeroAllocationsWhenNoEndpointHasCapacity() {
        assertArrayEquals(new long[]{0, 0},
                BatchDistributionPlanner.distribute(10, new long[]{0, 0}, 0));
    }
}
