package cn.ae2bc.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundRobinTest {
    @Test
    void cyclesEvenlyAcrossTargets() {
        int cursor = 0;
        int[] hits = new int[3];
        for (int i = 0; i < 12; i++) {
            int selected = RoundRobin.index(cursor, 0, hits.length);
            hits[selected]++;
            cursor = RoundRobin.advance(selected, hits.length);
        }
        assertEquals(4, hits[0]);
        assertEquals(4, hits[1]);
        assertEquals(4, hits[2]);
    }

    @Test
    void continuesAfterSkippedTarget() {
        assertEquals(2, RoundRobin.index(1, 1, 3));
        assertEquals(0, RoundRobin.advance(2, 3));
    }

    @Test
    void rejectsEmptyTargetList() {
        assertThrows(IllegalArgumentException.class, () -> RoundRobin.index(0, 0, 0));
    }

    @Test
    void handlesCursorAdditionWithoutOverflow() {
        assertEquals(1, RoundRobin.index(Integer.MAX_VALUE, 2, 4));
        assertEquals(2, RoundRobin.index(Integer.MIN_VALUE, -2, 4));
    }
}
