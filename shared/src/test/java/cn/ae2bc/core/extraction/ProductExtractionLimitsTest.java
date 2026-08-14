package cn.ae2bc.core.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProductExtractionLimitsTest {
    @Test
    void clampsIntervalAndAmountAtSharedBounds() {
        assertEquals(ProductExtractionLimits.MIN_INTERVAL, ProductExtractionLimits.clampInterval(Integer.MIN_VALUE));
        assertEquals(ProductExtractionLimits.MAX_INTERVAL, ProductExtractionLimits.clampInterval(Integer.MAX_VALUE));
        assertEquals(ProductExtractionLimits.MIN_AMOUNT, ProductExtractionLimits.clampAmount(Integer.MIN_VALUE));
        assertEquals(ProductExtractionLimits.MAX_AMOUNT, ProductExtractionLimits.clampAmount(Integer.MAX_VALUE));
    }

    @Test
    void allowsTheFullPositiveIntegerRangeForExtractionAmount() {
        assertEquals(Integer.MAX_VALUE, ProductExtractionLimits.MAX_AMOUNT);
        assertEquals(Integer.MAX_VALUE, ProductExtractionLimits.clampAmount(Integer.MAX_VALUE));
        assertEquals(ProductExtractionLimits.MIN_AMOUNT, ProductExtractionLimits.clampAmount(0));
    }

    @Test
    void boundsPerEndpointAndPerGridExtractionWork() {
        assertEquals(64, ProductExtractionLimits.MAX_TRANSFER_ROUNDS_PER_ENDPOINT_TICK);
        assertEquals(256, ProductExtractionLimits.MAX_TRANSFER_ROUNDS_PER_GRID_TICK);
    }
}
