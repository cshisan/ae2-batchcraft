package cn.ae2bc.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cn.ae2bc.core.extraction.ProductExtractionLimits;

public final class EndpointProductExtractionSettingsTest {
    @Test
    public void valuesAreClampedAndRevisionIsNeverNegative() {
        EndpointProductExtractionSettings settings = new EndpointProductExtractionSettings(
                true, Integer.MIN_VALUE, Integer.MAX_VALUE, -1);

        assertTrue(settings.isEnabled());
        assertEquals(ProductExtractionLimits.MIN_INTERVAL, settings.getInterval());
        assertEquals(ProductExtractionLimits.MAX_AMOUNT, settings.getAmount());
        assertEquals(0, settings.getRevision());
    }

    @Test
    public void valueComparisonUsesTheSameClampingRules() {
        EndpointProductExtractionSettings settings = new EndpointProductExtractionSettings(
                false, ProductExtractionLimits.MIN_INTERVAL,
                ProductExtractionLimits.MAX_AMOUNT, 7);

        assertTrue(settings.hasSameValues(false, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(settings.hasSameValues(true, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
}
