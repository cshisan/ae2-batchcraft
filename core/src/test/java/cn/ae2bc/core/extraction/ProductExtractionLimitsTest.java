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
}
