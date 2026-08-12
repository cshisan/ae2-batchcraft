package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EndpointProductExtractionSettingsTest {
    @Test
    void defaultsAreDisabledAndUseTheSharedLimits() {
        var settings = EndpointProductExtractionSettings.DEFAULT;

        assertFalse(settings.enabled());
        assertEquals(ProductExtractionSettings.DEFAULT_INTERVAL, settings.interval());
        assertEquals(ProductExtractionSettings.DEFAULT_AMOUNT, settings.amount());
    }

    @Test
    void valuesAreClampedAndEndpointExtractionDoesNotAddATypeFilter() {
        var settings = new EndpointProductExtractionSettings(true, 0, Integer.MAX_VALUE, -1);

        assertEquals(ProductExtractionSettings.MIN_INTERVAL, settings.interval());
        assertEquals(ProductExtractionSettings.MAX_AMOUNT, settings.amount());
        assertEquals(0, settings.revision());
        assertFalse(settings.toExtractionSettings().whitelist());
        assertEquals(java.util.Set.of(), settings.toExtractionSettings().markers());
    }
}
