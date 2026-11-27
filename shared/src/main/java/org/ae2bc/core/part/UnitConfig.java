package org.ae2bc.core.part;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.unit.*;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public class UnitConfig {
    private final GeneralPageConfig general;
    private final UnitPageConfig unit;

    /* 默认配置 */
    public static final UnitConfig DEFAULT = new UnitConfig(InputConfig.DEFAULT);

    UnitConfig(
            ReturnMode returnMode, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam,
            ProductExtractionParam extractionParam, boolean blocking
    ) {
        this.general = new GeneralPageConfig(returnMode, extractionParam, blocking);
        this.unit = new UnitPageConfig(breakPickup, outputPortMode, redstonePortParam);
    }

    public UnitConfig(InputConfig config) {
        this(
                config.general().returnMode(), config.unit().breakPickup(),
                config.unit().outputPortMode(),
                config.unit().redstonePortParam(), config.general().extractionParam(), config.general().blocking()
        );
    }

    public UnitConfig(GeneralPageConfig general, UnitPageConfig unit) {
        this(
                general.returnMode(), unit.breakPickup(),
                unit.outputPortMode(),
                unit.redstonePortParam(), general.extractionParam(), general.blocking()
        );
    }

    public static UnitConfig restore(
            ReturnMode returnMode, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam,
            ProductExtractionParam productExtractionParam, boolean blocking
    ) {
        return new UnitConfig(
                returnMode, breakPickup,
                outputPortMode,
                redstonePortParam, productExtractionParam, blocking
        );
    }
}
