package org.ae2bc.core.part;

import org.ae2bc.common.Utils;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.core.unit.ReturnMode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public class GeneralPageConfig {
    private final ReturnMode returnMode;
    private final ProductExtractionParam extractionParam;
    private final boolean blocking;

    public GeneralPageConfig(ReturnMode returnMode, ProductExtractionParam extractionParam) {
        this(returnMode, extractionParam, true);
    }

    public GeneralPageConfig(ReturnMode returnMode, ProductExtractionParam extractionParam, boolean blocking) {
        this.blocking = blocking;
        this.returnMode = Utils.null2default(returnMode, ReturnMode.UNBLOCKED);
        this.extractionParam = ProductExtractionParam.of(
                (short) Utils.clamp(
                        extractionParam.getInterval(),
                        ProductExtractionConfigConstants.INTERVAL_MIN,
                        ProductExtractionConfigConstants.INTERVAL_MAX
                ),
                Utils.clamp(
                        extractionParam.getQuantity(),
                        ProductExtractionConfigConstants.QUANTITY_MIN,
                        ProductExtractionConfigConstants.QUANTITY_MAX
                )
        );
    }

    public GeneralPageConfig withBlocking(boolean blocking) {
        return new GeneralPageConfig(this.returnMode, this.extractionParam, blocking);
    }
}
