package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductExtractionBackoffTest {
    @Test
    void emptyPollingBacksOffAndProgressResetsIt() {
        var backoff = new ProductExtractionBackoff();

        assertEquals(1, backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1));
        assertEquals(2, backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1));
        assertEquals(4, backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1));
        assertEquals(1, backoff.nextDelay(ProductExtractionTickState.PROGRESSED, 1));
        assertEquals(1, backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1));
    }

    @Test
    void configuredIntervalAndIdleDelayAreBothRespected() {
        var backoff = new ProductExtractionBackoff();
        for (int i = 0; i < 10; i++) {
            backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1);
        }

        assertEquals(ProductExtractionBackoff.MAX_IDLE_DELAY,
                backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 1));
        assertEquals(40, backoff.nextDelay(ProductExtractionTickState.NO_PROGRESS, 40));
        assertEquals(1, backoff.nextDelay(ProductExtractionTickState.WAITING, 40));
    }
}
