package org.ae2bc.core.part;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.ae2bc.common.Version;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.core.constant.UnitConfigConstants;
import org.ae2bc.core.unit.*;

@Getter
@Accessors(fluent = true)
public class UnitConfig implements Version {
    private final int version;
    private final GeneralPageConfig general;
    private final UnitPageConfig unit;

    /* 默认配置 */
    public static final UnitConfig DEFAULT = new UnitConfig(
            false, 0, ReturnMode.UNBLOCKED, true,
            OutputPortMode.NORMAL,
            RedstonePortParam.of(
                    RedstoneMode.ONCE,
                    UnitConfigConstants.REDSTONE_STRENGTH_MAX,
                    UnitConfigConstants.REDSTONE_PULSE_WIDTH_MIN,
                    UnitConfigConstants.REDSTONE_PULSE_PERIOD_MAX
            ),
            ProductExtractionParam.of(
                    ProductExtractionConfigConstants.INTERVAL_DEFAULT,
                    ProductExtractionConfigConstants.QUANTITY_DEFAULT
            ), true
    );

    UnitConfig(
            boolean incrementVersion, int version, ReturnMode returnMode, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam,
            ProductExtractionParam extractionParam, boolean blocking
    ) {
        this.version = incrementVersion ? Version.nextLocal(version) : Version.restoreAny(version);
        this.general = new GeneralPageConfig(returnMode, extractionParam, blocking);
        this.unit = new UnitPageConfig(breakPickup, outputPortMode, redstonePortParam);
    }

    public UnitConfig(InputConfig config) {
        this(
                false, config.version(), config.general().returnMode(), config.unit().breakPickup(),
                config.unit().outputPortMode(),
                config.unit().redstonePortParam(), config.general().extractionParam(), config.general().blocking()
        );
    }

    public UnitConfig(int version, GeneralPageConfig general, UnitPageConfig unit) {
        this(
                true, version, general.returnMode(), unit.breakPickup(),
                unit.outputPortMode(),
                unit.redstonePortParam(), general.extractionParam(), general.blocking()
        );
    }

    public static UnitConfig restore(
            int version, ReturnMode returnMode, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam,
            ProductExtractionParam productExtractionParam, boolean blocking
    ) {
        return new UnitConfig(
                false, version, returnMode, breakPickup,
                outputPortMode,
                redstonePortParam, productExtractionParam, blocking
        );
    }
}
