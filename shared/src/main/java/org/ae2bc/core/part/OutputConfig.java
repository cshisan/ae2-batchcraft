package org.ae2bc.core.part;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.ae2bc.common.Version;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.core.unit.*;

@Getter
@Accessors(fluent = true)
public class OutputConfig implements Version {
    private final int version;
    private final GeneralPageConfig general;
    private final OutputPageConfig output;

    /* 默认配置 */
    public static final OutputConfig DEFAULT = new OutputConfig(
            false, 0, ReturnMode.UNBLOCKED, true,
            ProductExtractionParam.of(
                    ProductExtractionConfigConstants.INTERVAL_DEFAULT,
                    ProductExtractionConfigConstants.QUANTITY_DEFAULT
            ), true
    );

    OutputConfig(boolean incrementVersion, int version, ReturnMode returnMode, boolean productExtraction, ProductExtractionParam extractionParam, boolean blocking) {
        this.version = incrementVersion
                ? Version.nextLocal(version)
                : Version.restoreAny(version);
        this.general = new GeneralPageConfig(returnMode, extractionParam, blocking);
        this.output = new OutputPageConfig(productExtraction);
    }

    public OutputConfig(InputConfig config) {
        this(
                false, config.version(), config.general().returnMode(),
                config.output().productExtraction(), config.general().extractionParam(), config.general().blocking()
        );
    }

    public OutputConfig(int version, GeneralPageConfig general, OutputPageConfig output) {
        this(
                true, version, general.returnMode(),
                output.productExtraction(), general.extractionParam(), general.blocking()
        );
    }

    public static OutputConfig restore(
            int version, ReturnMode returnMode,
            boolean productExtraction,
            ProductExtractionParam extractionParam, boolean blocking
    ) {
        return new OutputConfig(false, version, returnMode, productExtraction, extractionParam, blocking);
    }
}
