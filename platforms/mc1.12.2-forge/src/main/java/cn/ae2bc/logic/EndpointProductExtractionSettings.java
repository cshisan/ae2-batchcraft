package cn.ae2bc.logic;

import cn.ae2bc.core.extraction.ProductExtractionLimits;

/** Product extraction settings owned by one Pattern P2P input frequency. */
public final class EndpointProductExtractionSettings {
    public static final EndpointProductExtractionSettings DEFAULT =
            new EndpointProductExtractionSettings(false,
                    ProductExtractionLimits.DEFAULT_INTERVAL,
                    ProductExtractionLimits.DEFAULT_AMOUNT, 0);

    private final boolean enabled;
    private final int interval;
    private final int amount;
    private final long revision;

    public EndpointProductExtractionSettings(boolean enabled, int interval, int amount, long revision) {
        this.enabled = enabled;
        this.interval = ProductExtractionLimits.clampInterval(interval);
        this.amount = ProductExtractionLimits.clampAmount(amount);
        this.revision = Math.max(0, revision);
    }

    public boolean isEnabled() { return enabled; }
    public int getInterval() { return interval; }
    public int getAmount() { return amount; }
    public long getRevision() { return revision; }

    public boolean hasSameValues(boolean nextEnabled, int nextInterval, int nextAmount) {
        return enabled == nextEnabled
                && interval == ProductExtractionLimits.clampInterval(nextInterval)
                && amount == ProductExtractionLimits.clampAmount(nextAmount);
    }
}
