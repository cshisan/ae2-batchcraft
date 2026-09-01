package cn.ae2bc.link;

import cn.ae2bc.core.dispatch.RoundRobinPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundRobinTest {
    @Test
    void cyclesEvenlyAcrossTargets() {
        int cursor = 0;
        int[] hits = new int[3];
        for (int i = 0; i < 12; i++) {
            int selected = RoundRobinPolicy.index(cursor, 0, hits.length);
            hits[selected]++;
            cursor = RoundRobinPolicy.advance(selected, hits.length);
        }
        assertEquals(4, hits[0]);
        assertEquals(4, hits[1]);
        assertEquals(4, hits[2]);
    }

    @Test
    void continuesAfterSkippedTarget() {
        assertEquals(2, RoundRobinPolicy.index(1, 1, 3));
        assertEquals(0, RoundRobinPolicy.advance(2, 3));
    }

    @Test
    void rejectsEmptyTargetList() {
        assertThrows(IllegalArgumentException.class, () -> RoundRobinPolicy.index(0, 0, 0));
    }

    @Test
    void handlesCursorAdditionWithoutOverflow() {
        assertEquals(1, RoundRobinPolicy.index(Integer.MAX_VALUE, 2, 4));
        assertEquals(2, RoundRobinPolicy.index(Integer.MIN_VALUE, -2, 4));
    }
}
