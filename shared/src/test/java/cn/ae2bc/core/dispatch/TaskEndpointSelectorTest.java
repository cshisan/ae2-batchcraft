package cn.ae2bc.core.dispatch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskEndpointSelectorTest {
    @Test
    void roundRobinStartsAtCursorAndPriorityAlwaysStartsFirst() {
        List<Integer> roundRobinVisited = new ArrayList<Integer>();
        int roundRobin = TaskEndpointSelector.select(TaskAllocationMode.ROUND_ROBIN, 2, 4,
                null, index -> {
                    roundRobinVisited.add(index);
                    return index == 0;
                });
        List<Integer> priorityVisited = new ArrayList<Integer>();
        int priority = TaskEndpointSelector.select(TaskAllocationMode.PRIORITY, 2, 4,
                null, index -> {
                    priorityVisited.add(index);
                    return index == 2;
                });

        assertEquals(0, roundRobin);
        assertEquals(Arrays.asList(2, 3, 0), roundRobinVisited);
        assertEquals(2, priority);
        assertEquals(Arrays.asList(0, 1, 2), priorityVisited);
    }

    @Test
    void randomStartsAtRandomIndexAndRetriesOtherEndpoints() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> selected = new ArrayList<Integer>();
        for (int i = 0; i < 3; i++) {
            selected.add(TaskEndpointSelector.select(TaskAllocationMode.RANDOM, 0, 4,
                    bound -> 2, index -> {
                        attempts.incrementAndGet();
                    return index == 0;
                    }));
        }

        assertEquals(Arrays.asList(0, 0, 0), selected);
        assertEquals(9, attempts.get());
    }

    @Test
    void randomReturnsFailureAfterTryingEveryEndpoint() {
        AtomicInteger attempts = new AtomicInteger();
        assertEquals(-1, TaskEndpointSelector.select(TaskAllocationMode.RANDOM, 0, 3,
                bound -> 1, index -> {
                    attempts.incrementAndGet();
                    return false;
                }));
        assertEquals(3, attempts.get());
    }

    @Test
    void rejectsInvalidRandomIndexes() {
        assertThrows(IllegalArgumentException.class, () -> TaskEndpointSelector.select(
                TaskAllocationMode.RANDOM, 0, 2, bound -> 2, index -> true));
    }
}
