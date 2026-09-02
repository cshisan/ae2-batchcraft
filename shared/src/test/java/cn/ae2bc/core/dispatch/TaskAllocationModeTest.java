package cn.ae2bc.core.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskAllocationModeTest {
    @Test
    void usesStableIdsAndFallsBackToRoundRobin() {
        assertEquals(0, TaskAllocationMode.ROUND_ROBIN.getId());
        assertEquals(1, TaskAllocationMode.RANDOM.getId());
        assertEquals(2, TaskAllocationMode.PRIORITY.getId());
        assertEquals(TaskAllocationMode.ROUND_ROBIN, TaskAllocationMode.fromId(-1));
        assertEquals(TaskAllocationMode.ROUND_ROBIN, TaskAllocationMode.fromId(99));
    }

    @Test
    void cyclesInGuiOrder() {
        assertEquals(TaskAllocationMode.RANDOM, TaskAllocationMode.ROUND_ROBIN.next());
        assertEquals(TaskAllocationMode.PRIORITY, TaskAllocationMode.RANDOM.next());
        assertEquals(TaskAllocationMode.ROUND_ROBIN, TaskAllocationMode.PRIORITY.next());
    }
}
