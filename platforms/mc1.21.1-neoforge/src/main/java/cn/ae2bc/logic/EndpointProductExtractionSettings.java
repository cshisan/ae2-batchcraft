package cn.ae2bc.logic;

import java.util.Set;

/** Extraction switch and schedule shared by normal outputs on one pattern P2P frequency. */
public record EndpointProductExtractionSettings(boolean enabled, int interval, int amount, long revision) {
    public static final EndpointProductExtractionSettings DEFAULT = new EndpointProductExtractionSettings(
            false, ProductExtractionSettings.DEFAULT_INTERVAL, ProductExtractionSettings.DEFAULT_AMOUNT, 0);

    public EndpointProductExtractionSettings {
        interval = ProductExtractionSettings.clampInterval(interval);
        amount = ProductExtractionSettings.clampAmount(amount);
        revision = Math.max(0, revision);
    }

    public ProductExtractionSettings toExtractionSettings() {
        return new ProductExtractionSettings(enabled, interval, amount, false, Set.of());
    }
}
