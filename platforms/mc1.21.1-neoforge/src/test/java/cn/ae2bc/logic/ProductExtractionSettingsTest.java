package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductExtractionSettingsTest {
    @Test
    void clampsConfiguredBounds() {
        assertEquals(1, ProductExtractionSettings.clampInterval(-1));
        assertEquals(2000, ProductExtractionSettings.clampInterval(3000));
        assertEquals(1, ProductExtractionSettings.clampAmount(-1));
        assertEquals(64, ProductExtractionSettings.clampAmount(100));
    }

    @Test
    void normalizesConstructorValuesAndKeepsMarkersImmutable() {
        var settings = new ProductExtractionSettings(true, -1, 100, false, Set.of());

        assertEquals(ProductExtractionSettings.MIN_INTERVAL, settings.interval());
        assertEquals(ProductExtractionSettings.MAX_AMOUNT, settings.amount());
        assertThrows(UnsupportedOperationException.class, settings.markers()::clear);
    }
}
