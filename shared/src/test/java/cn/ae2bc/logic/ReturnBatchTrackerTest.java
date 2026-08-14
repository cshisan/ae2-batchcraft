package cn.ae2bc.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ReturnBatchTrackerTest {
    @Test
    void idleNeverBlocksReturnsInAnyConfiguredMode() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();

        assertEquals(12, tracker.filter("unrelated", 12, ReturnMode.STRICT));
    }

    @Test
    void activeBatchAcceptsOnlyTheSamePatternAndDeclaredOutputs() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        Map<String, Long> outputs = outputs("result", 4L, "byproduct", 1L);

        assertTrue(tracker.begin("pattern-a", outputs, "result", 4));
        assertTrue(tracker.canAccept("pattern-a", outputs, "result", 4));
        assertFalse(tracker.canAccept("pattern-b", outputs("other", 2L), "other", 2));
    }

    @Test
    void strictModeFiltersOnlyByDeclaredOutputType() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        Map<String, Long> outputs = outputs("result", 4L, "byproduct", 1L);
        assertTrue(tracker.begin("pattern", outputs, "result", 4));
        assertTrue(tracker.begin("pattern", outputs, "result", 4));

        assertEquals(9, tracker.filter("result", 9, ReturnMode.STRICT));
        assertEquals(20, tracker.filter("byproduct", 20, ReturnMode.STRICT));
        assertEquals(0, tracker.filter("unrelated", 20, ReturnMode.STRICT));
        assertFalse(tracker.returned("byproduct", 20));
        assertFalse(tracker.returned("result", 3));
        assertTrue(tracker.returned("result", 100));
        assertFalse(tracker.isActive());
    }

    @Test
    void configuredModeChangesApplyToAnActiveBatch() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        assertTrue(tracker.begin("pattern", outputs("result", 1L), "result", 1));

        assertEquals(5, tracker.filter("unrelated", 5, ReturnMode.UNBLOCKED));
        assertEquals(0, tracker.filter("unrelated", 5, ReturnMode.STRICT));
        assertEquals(5, tracker.filter("result", 5, ReturnMode.STRICT));
    }

    @Test
    void primaryOutputsCompleteOneBatchCumulatively() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        assertTrue(tracker.begin("pattern", outputs("result", 3L), "result", 3));

        assertFalse(tracker.returned("result", 1));
        assertFalse(tracker.returned("result", 1));
        assertTrue(tracker.returned("result", 1));
    }

    @Test
    void capsOneBatchAtSixtyFourTasksAndCanRollbackOneAdmission() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        Map<String, Long> outputs = outputs("result", 1L);
        for (int i = 0; i < ReturnBatchTracker.MAX_TASKS; i++) {
            assertTrue(tracker.begin("pattern", outputs, "result", 1));
        }
        assertFalse(tracker.canAccept("pattern", outputs, "result", 1));

        tracker.rollback(1);

        assertEquals(ReturnBatchTracker.MAX_TASKS - 1, tracker.getTaskCount());
        assertTrue(tracker.canAccept("pattern", outputs, "result", 1));
    }

    @Test
    void appendsDifferentSizedRoundsWithoutCreatingLogicalTasks() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        assertTrue(tracker.begin("pattern", outputs("result", 8L, "byproduct", 2L), "result", 8));

        Map<String, Long> appended = outputs("result", 3L, "byproduct", 1L);
        assertTrue(tracker.canAppend("pattern", appended, "result", 3));
        assertTrue(tracker.append("pattern", appended, "result", 3));

        assertEquals(1, tracker.getTaskCount());
        assertEquals(11, tracker.getExpectedPrimary());
        assertEquals(outputs("result", 11L, "byproduct", 3L), tracker.getDeclaredOutputs());
    }

    @Test
    void rollsBackAnAppendedRoundExactly() {
        ReturnBatchTracker<String, String> tracker = new ReturnBatchTracker<String, String>();
        Map<String, Long> first = outputs("result", 8L, "byproduct", 2L);
        Map<String, Long> appended = outputs("result", 3L, "byproduct", 1L);
        assertTrue(tracker.begin("pattern", first, "result", 8));
        assertTrue(tracker.append("pattern", appended, "result", 3));

        tracker.rollbackAppend(appended, 3);

        assertEquals(1, tracker.getTaskCount());
        assertEquals(8, tracker.getExpectedPrimary());
        assertEquals(first, tracker.getDeclaredOutputs());
    }

    private static Map<String, Long> outputs(String key, long amount) {
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        result.put(key, amount);
        return result;
    }

    private static Map<String, Long> outputs(String firstKey, long firstAmount,
            String secondKey, long secondAmount) {
        Map<String, Long> result = outputs(firstKey, firstAmount);
        result.put(secondKey, secondAmount);
        return result;
    }
}
