package org.ae2bc.core.part;

import org.ae2bc.common.Utils;
import org.ae2bc.core.constant.UnitConfigConstants;
import org.ae2bc.core.unit.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public class UnitPageConfig {
    private final boolean breakPickup;
    private final OutputPortMode outputPortMode;
    private final RedstonePortParam redstonePortParam;

    public UnitPageConfig(
            boolean breakPickup, OutputPortMode outputPortMode, RedstonePortParam redstonePortParam
    ) {
        this.breakPickup = breakPickup;
        this.outputPortMode = Utils.null2default(outputPortMode, OutputPortMode.NORMAL);
        this.redstonePortParam = RedstonePortParam.of(
                Utils.null2default(redstonePortParam.getMode(), RedstoneMode.ONCE),
                (short) Utils.clamp(
                        redstonePortParam.getStrength(),
                        UnitConfigConstants.REDSTONE_STRENGTH_MIN,
                        UnitConfigConstants.REDSTONE_STRENGTH_MAX
                ),
                // 脉冲宽度最大值由of方法内部的脉冲间隔限定
                (short) Math.max(
                        redstonePortParam.getPulseWidth(),
                        UnitConfigConstants.REDSTONE_PULSE_WIDTH_MIN
                ),
                (short) Utils.clamp(
                        redstonePortParam.getPulsePeriod(),
                        UnitConfigConstants.REDSTONE_PULSE_PERIOD_MIN,
                        UnitConfigConstants.REDSTONE_PULSE_PERIOD_MAX
                )
        );
    }
}
