package cn.ae2bc.core.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtractionDeadlineGateTest {
    @Test
    void usesAbsoluteDeadlinesAndCanWake() {
        ExtractionDeadlineGate gate = new ExtractionDeadlineGate();
        assertTrue(gate.isDue(100, 20));
        assertFalse(gate.isDue(119, 20));
        assertTrue(gate.isDue(120, 20));
        gate.wake();
        assertTrue(gate.isDue(120, 20));
    }
}
