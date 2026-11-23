package org.ae2bc.core.common;

import lombok.Getter;

@Getter
public class ProductExtractionParam {
    private final short interval;
    private final int quantity;

    public ProductExtractionParam(short interval, int quantity) {
        this.interval = interval;
        this.quantity = quantity;
    }

    public static ProductExtractionParam of(short interval, int quantity) {
        return new ProductExtractionParam(interval, quantity);
    }
}
