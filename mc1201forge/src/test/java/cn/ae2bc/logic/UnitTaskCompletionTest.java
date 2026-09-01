package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTaskCompletionTest {
    @Test
    void waitsForBothReturnedProductAndDispatchedInputs() {
        assertFalse(UnitTaskCompletion.isComplete(true, 1, false));
        assertFalse(UnitTaskCompletion.isComplete(true, 0, true));
        assertTrue(UnitTaskCompletion.isComplete(true, 0, false));
    }

    @Test
    void inactiveStateCannotCompleteAgain() {
        assertFalse(UnitTaskCompletion.isComplete(false, 0, false));
    }
}
