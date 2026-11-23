package org.ae2bc.core.part;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.ae2bc.common.Version;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.core.constant.UnitConfigConstants;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.unit.*;

@Getter
@Accessors(fluent = true)
public class InputConfig implements Version {
    private final int version;
    private final GeneralPageConfig general;
    private final OutputPageConfig output;
    private final UnitPageConfig unit;
    private final TaskAllocationMode taskAllocationMode;
    private final TaskSplitMode taskSplitMode;

    /* 默认配置 */
    public static final InputConfig DEFAULT = new InputConfig(
            false, 0, ReturnMode.UNBLOCKED, false, true,
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
            ),
            TaskAllocationMode.ROUND_ROBIN,
            TaskSplitMode.FULL, true
    );

    InputConfig(
            boolean incrementVersion, int version, ReturnMode returnMode, boolean productExtraction, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam, ProductExtractionParam extractionParam,
            TaskAllocationMode taskAllocationMode,
            TaskSplitMode taskSplitMode, boolean blocking
    ) {
        this.version = incrementVersion
                ? Version.nextInput(version)
                : Version.restoreInput(version);
        this.general = new GeneralPageConfig(returnMode, extractionParam, blocking);
        this.output = new OutputPageConfig(productExtraction);
        this.unit = new UnitPageConfig(breakPickup, outputPortMode, redstonePortParam);
        this.taskAllocationMode = taskAllocationMode == null
                ? TaskAllocationMode.ROUND_ROBIN
                : taskAllocationMode;
        this.taskSplitMode = taskSplitMode == null ? TaskSplitMode.FULL : taskSplitMode;
    }

    public InputConfig(InputConfig config) {
        this(
                true, config.version, config.general.returnMode(),
                config.output.productExtraction(), config.unit.breakPickup(),
                config.unit.outputPortMode(),
                config.unit.redstonePortParam(), config.general.extractionParam(),
                config.taskAllocationMode,
                config.taskSplitMode, config.general.blocking()
        );
    }

    public InputConfig(short version, GeneralPageConfig general, OutputPageConfig output, UnitPageConfig unit) {
        this(version, general, output, unit, TaskAllocationMode.ROUND_ROBIN, TaskSplitMode.FULL);
    }

    public InputConfig(
            short version, GeneralPageConfig general, OutputPageConfig output, UnitPageConfig unit,
            TaskAllocationMode taskAllocationMode
    ) {
        this(version, general, output, unit, taskAllocationMode, TaskSplitMode.FULL);
    }

    public InputConfig(
            int version, GeneralPageConfig general, OutputPageConfig output, UnitPageConfig unit,
            TaskAllocationMode taskAllocationMode, TaskSplitMode taskSplitMode
    ) {
        this(
                true, version, general.returnMode(),
                output.productExtraction(), unit.breakPickup(),
                unit.outputPortMode(),
                unit.redstonePortParam(), general.extractionParam(), taskAllocationMode, taskSplitMode, general.blocking()
        );
    }

    public static InputConfig restore(
            int version, ReturnMode returnMode, boolean productExtraction, boolean breakPickup,
            OutputPortMode outputPortMode, RedstonePortParam redstonePortParam, ProductExtractionParam extractionParam,
            TaskAllocationMode taskAllocationMode, TaskSplitMode taskSplitMode, boolean blocking
    ) {
        return new InputConfig(
                false, version, returnMode, productExtraction, breakPickup,
                outputPortMode,
                redstonePortParam, extractionParam, taskAllocationMode, taskSplitMode, blocking
        );
    }

    public static Builder builder(InputConfig config) {
        return new Builder(config);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static final class Builder {
        private final int version;
        private TaskAllocationMode taskAllocationMode;
        private TaskSplitMode taskSplitMode;
        private ReturnMode returnMode;
        private boolean productExtraction;
        private boolean breakPickup;
        private boolean blocking;
        private OutputPortMode outputPortMode;
        private RedstoneMode redstonePortMode;
        private short redstonePortStrength;
        private short redstonePortPulseWidth;
        private short redstonePortPulsePeriod;
        private short productExtractionInterval;
        private int productExtractionQuantity;

        private Builder(InputConfig config) {
            this.version = config.version();
            this.taskAllocationMode = config.taskAllocationMode;
            this.taskSplitMode = config.taskSplitMode;
            this.returnMode = config.general().returnMode();
            this.blocking = config.general().blocking();
            this.productExtraction = config.output().productExtraction();
            this.breakPickup = config.unit().breakPickup();
            this.outputPortMode = config.unit().outputPortMode();

            RedstonePortParam redstonePortParam = config.unit().redstonePortParam();
            this.redstonePortMode = redstonePortParam.getMode();
            this.redstonePortStrength = redstonePortParam.getStrength();
            this.redstonePortPulseWidth = redstonePortParam.getPulseWidth();
            this.redstonePortPulsePeriod = redstonePortParam.getPulsePeriod();

            ProductExtractionParam extractionParam = config.general().extractionParam();
            this.productExtractionInterval = extractionParam.getInterval();
            this.productExtractionQuantity = extractionParam.getQuantity();
        }

        public InputConfig build() {
            return new InputConfig(
                    true, this.version, this.returnMode, this.productExtraction,
                    this.breakPickup, this.outputPortMode,
                    RedstonePortParam.of(
                            this.redstonePortMode, this.redstonePortStrength,
                            this.redstonePortPulseWidth, this.redstonePortPulsePeriod
                    ),
                    ProductExtractionParam.of(this.productExtractionInterval, this.productExtractionQuantity),
                    this.taskAllocationMode, this.taskSplitMode, this.blocking
            );
        }
    }
}
