package org.ae2bc.core.part;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.unit.*;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public class OutputConfig {
    private final GeneralPageConfig general;
    private final OutputPageConfig output;

    /* 默认配置 */
    public static final OutputConfig DEFAULT = new OutputConfig(InputConfig.DEFAULT);

    OutputConfig(ReturnMode returnMode, boolean productExtraction, ProductExtractionParam extractionParam, boolean blocking) {
        this.general = new GeneralPageConfig(returnMode, extractionParam, blocking);
        this.output = new OutputPageConfig(productExtraction);
    }

    public OutputConfig(InputConfig config) {
        this(
                config.general().returnMode(),
                config.output().productExtraction(), config.general().extractionParam(), config.general().blocking()
        );
    }

    public OutputConfig(GeneralPageConfig general, OutputPageConfig output) {
        this(
                general.returnMode(),
                output.productExtraction(), general.extractionParam(), general.blocking()
        );
    }

    public static OutputConfig restore(
            ReturnMode returnMode,
            boolean productExtraction,
            ProductExtractionParam extractionParam, boolean blocking
    ) {
        return new OutputConfig(returnMode, productExtraction, extractionParam, blocking);
    }
}
