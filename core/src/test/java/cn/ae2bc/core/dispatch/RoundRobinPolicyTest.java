package cn.ae2bc.core.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundRobinPolicyTest {
    @Test
    void wrapsCursorAndOffsetAcrossTheEndpointList() {
        assertEquals(2, RoundRobinPolicy.index(1, 1, 3));
        assertEquals(1, RoundRobinPolicy.index(Integer.MAX_VALUE, 2, 4));
        assertEquals(2, RoundRobinPolicy.index(Integer.MIN_VALUE, -2, 4));
    }

    @Test
    void advancesAfterACompletedEndpoint() {
        assertEquals(0, RoundRobinPolicy.advance(2, 3));
    }

    @Test
    void rejectsAnEmptyEndpointList() {
        assertThrows(IllegalArgumentException.class, () -> RoundRobinPolicy.index(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RoundRobinPolicy.advance(0, 0));
    }
}
