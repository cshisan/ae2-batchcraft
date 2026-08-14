package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointProductExtractionSettingsTest {
    @Test
    void defaultsAreDisabledAndUseTheSharedLimits() {
        EndpointProductExtractionSettings settings = EndpointProductExtractionSettings.DEFAULT;

        assertFalse(settings.enabled());
        assertEquals(cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_INTERVAL, settings.interval());
        assertEquals(cn.ae2bc.core.extraction.ProductExtractionLimits.DEFAULT_AMOUNT, settings.amount());
        assertFalse(settings.isEnabled());
        assertEquals(settings.interval(), settings.getInterval());
        assertEquals(settings.amount(), settings.getAmount());
    }

    @Test
    void valuesAreClampedAndHaveStableValueSemantics() {
        EndpointProductExtractionSettings settings =
                new EndpointProductExtractionSettings(true, 0, Integer.MAX_VALUE, -1);
        EndpointProductExtractionSettings equal =
                new EndpointProductExtractionSettings(true, 0, Integer.MAX_VALUE, -1);

        assertEquals(cn.ae2bc.core.extraction.ProductExtractionLimits.MIN_INTERVAL, settings.interval());
        assertEquals(cn.ae2bc.core.extraction.ProductExtractionLimits.MAX_AMOUNT, settings.amount());
        assertEquals(0, settings.revision());
        assertTrue(settings.hasSameValues(true, 0, Integer.MAX_VALUE));
        assertEquals(settings, equal);
        assertEquals(settings.hashCode(), equal.hashCode());
        assertNotEquals(settings, EndpointProductExtractionSettings.DEFAULT);
    }
}
