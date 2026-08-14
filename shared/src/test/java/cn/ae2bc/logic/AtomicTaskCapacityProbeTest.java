package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicTaskCapacityProbeTest {
    @Test
    void rejectsNonPositiveUpperBoundsWithoutProbing() {
        List<Long> probes = new ArrayList<>();

        assertEquals(0, AtomicTaskCapacityProbe.findMaximum(0, value -> probes.add(value)));
        assertEquals(Collections.emptyList(), probes);
    }

    @Test
    void returnsZeroWhenOneAtomicTaskDoesNotFit() {
        List<Long> probes = new ArrayList<>();

        assertEquals(0, AtomicTaskCapacityProbe.findMaximum(10, value -> {
            probes.add(value);
            return false;
        }));
        assertEquals(Collections.singletonList(1L), probes);
    }

    @Test
    void findsNonPowerOfTwoCapacityWithinLocalInterval() {
        List<Long> probes = new ArrayList<>();

        assertEquals(10, AtomicTaskCapacityProbe.findMaximum(10_000, value -> {
            probes.add(value);
            return value <= 10;
        }));
        assertEquals(Arrays.asList(1L, 2L, 4L, 8L, 16L, 12L, 10L, 11L), probes);
    }

    @Test
    void returnsPowerOfTwoAfterFirstFailedExpansion() {
        assertEquals(8, AtomicTaskCapacityProbe.findMaximum(10_000, value -> value <= 8));
    }

    @Test
    void returnsUpperBoundWhenAllTasksFit() {
        assertEquals(999, AtomicTaskCapacityProbe.findMaximum(999, value -> true));
    }

    @Test
    void handlesLongMaximumWithoutOverflow() {
        assertEquals(Long.MAX_VALUE,
                AtomicTaskCapacityProbe.findMaximum(Long.MAX_VALUE, value -> true));
        assertEquals(Long.MAX_VALUE - 1,
                AtomicTaskCapacityProbe.findMaximum(Long.MAX_VALUE, value -> value < Long.MAX_VALUE));
    }
}
