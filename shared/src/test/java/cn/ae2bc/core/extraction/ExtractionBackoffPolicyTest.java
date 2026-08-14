package cn.ae2bc.core.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractionBackoffPolicyTest {
    @Test
    void backsOffEmptyPollingAndResetsAfterProgress() {
        ExtractionBackoffPolicy policy = new ExtractionBackoffPolicy();
        assertEquals(1, policy.nextDelay(ExtractionOutcome.NO_PROGRESS, 1));
        assertEquals(2, policy.nextDelay(ExtractionOutcome.NO_PROGRESS, 1));
        assertEquals(4, policy.nextDelay(ExtractionOutcome.NO_PROGRESS, 1));
        assertEquals(1, policy.nextDelay(ExtractionOutcome.PROGRESSED, 1));
        assertEquals(1, policy.nextDelay(ExtractionOutcome.BUDGET_EXHAUSTED, 40));
        assertEquals(1, policy.nextDelay(ExtractionOutcome.NO_PROGRESS, 1));
    }
}
