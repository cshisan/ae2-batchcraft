package cn.ae2bc.core.extraction;

import cn.ae2bc.logic.Numbers;

/** Shared bounds for product extraction settings. Platform code owns serialization and item APIs. */
public final class ProductExtractionLimits {
    public static final int DEFAULT_INTERVAL = 20;
    public static final int DEFAULT_AMOUNT = 64;
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 2000;
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 64;

    private ProductExtractionLimits() {
    }

    public static int clampInterval(int value) {
        return Numbers.clamp(value, MIN_INTERVAL, MAX_INTERVAL);
    }

    public static int clampAmount(int value) {
        return Numbers.clamp(value, MIN_AMOUNT, MAX_AMOUNT);
    }
}
