package org.ae2bc.core.part;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class OutputPageConfig {
    private final boolean productExtraction;

    public OutputPageConfig(boolean productExtraction) {
        this.productExtraction = productExtraction;
    }
}
