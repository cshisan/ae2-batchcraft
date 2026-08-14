package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchBackoffPolicyTest {
    @Test
    void planningBackoffIsBounded() {
        assertEquals(5, DispatchBackoffPolicy.planningDelay(1));
        assertEquals(10, DispatchBackoffPolicy.planningDelay(2));
        assertEquals(20, DispatchBackoffPolicy.planningDelay(3));
        assertEquals(40, DispatchBackoffPolicy.planningDelay(4));
        assertEquals(80, DispatchBackoffPolicy.planningDelay(5));
        assertEquals(100, DispatchBackoffPolicy.planningDelay(6));
        assertEquals(100, DispatchBackoffPolicy.planningDelay(30));
    }

    @Test
    void pendingBackoffRemainsResponsive() {
        assertEquals(1, DispatchBackoffPolicy.pendingDelay(1));
        assertEquals(2, DispatchBackoffPolicy.pendingDelay(2));
        assertEquals(4, DispatchBackoffPolicy.pendingDelay(3));
        assertEquals(8, DispatchBackoffPolicy.pendingDelay(4));
        assertEquals(16, DispatchBackoffPolicy.pendingDelay(5));
        assertEquals(20, DispatchBackoffPolicy.pendingDelay(6));
    }
}
