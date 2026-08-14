package cn.ae2bc.logic;

import cn.ae2bc.core.extraction.ProductExtractionLimits;

import java.util.Objects;

/** Extraction switch and schedule shared by normal outputs on one pattern P2P frequency. */
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

    public boolean enabled() { return enabled; }
    public int interval() { return interval; }
    public int amount() { return amount; }
    public long revision() { return revision; }

    public boolean isEnabled() { return enabled; }
    public int getInterval() { return interval; }
    public int getAmount() { return amount; }
    public long getRevision() { return revision; }

    public boolean hasSameValues(boolean nextEnabled, int nextInterval, int nextAmount) {
        return enabled == nextEnabled
                && interval == ProductExtractionLimits.clampInterval(nextInterval)
                && amount == ProductExtractionLimits.clampAmount(nextAmount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EndpointProductExtractionSettings)) return false;
        EndpointProductExtractionSettings that = (EndpointProductExtractionSettings) other;
        return enabled == that.enabled && interval == that.interval
                && amount == that.amount && revision == that.revision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, interval, amount, revision);
    }

    @Override
    public String toString() {
        return "EndpointProductExtractionSettings[enabled=" + enabled
                + ", interval=" + interval + ", amount=" + amount
                + ", revision=" + revision + ']';
    }
}
