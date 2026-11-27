package org.ae2bc.core.part;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public class OutputPageConfig {
    private final boolean productExtraction;

    public OutputPageConfig(boolean productExtraction) {
        this.productExtraction = productExtraction;
    }
}
