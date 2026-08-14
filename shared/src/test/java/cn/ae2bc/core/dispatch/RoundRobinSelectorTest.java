package cn.ae2bc.core.dispatch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundRobinSelectorTest {
    @Test
    void startsAtTheCursorAndStopsAtTheFirstAcceptedCandidate() {
        final List<Integer> visited = new ArrayList<Integer>();
        int selected = RoundRobinSelector.select(2, 4, index -> {
            visited.add(index);
            return index == 0;
        });
        assertEquals(0, selected);
        assertEquals(Arrays.asList(2, 3, 0), visited);
    }

    @Test
    void reportsNoSelectionWhenEveryCandidateRejects() {
        assertEquals(-1, RoundRobinSelector.select(0, 3, index -> false));
        assertEquals(-1, RoundRobinSelector.select(0, 0, index -> true));
    }

    @Test
    void retainsUnavailableCandidatesInTheStableRotation() {
        boolean[] available = { true, false, true };
        int cursor = 0;

        int first = RoundRobinSelector.select(cursor, available.length, index -> available[index]);
        cursor = RoundRobinPolicy.advance(first, available.length);
        int second = RoundRobinSelector.select(cursor, available.length, index -> available[index]);
        cursor = RoundRobinPolicy.advance(second, available.length);
        available[1] = true;
        int third = RoundRobinSelector.select(cursor, available.length, index -> available[index]);
        cursor = RoundRobinPolicy.advance(third, available.length);
        int fourth = RoundRobinSelector.select(cursor, available.length, index -> available[index]);

        assertEquals(Arrays.asList(0, 2, 0, 1), Arrays.asList(first, second, third, fourth));
    }
}
