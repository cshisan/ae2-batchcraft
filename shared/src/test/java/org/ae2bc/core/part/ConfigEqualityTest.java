package org.ae2bc.core.part;

import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.ReturnMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ConfigEqualityTest {
    @Test
    void independentlyConstructedNestedValuesAreEqualAndHaveEqualHashes() {
        InputConfig original = InputConfig.DEFAULT;
        InputConfig copy = InputConfig.builder(original).build();
        Assertions.assertNotSame(original.general(), copy.general());
        Assertions.assertNotSame(original.unit().redstonePortParam(), copy.unit().redstonePortParam());
        Assertions.assertEquals(original, copy);
        Assertions.assertEquals(original.hashCode(), copy.hashCode());
        Assertions.assertEquals(new OutputConfig(original), new OutputConfig(copy));
        Assertions.assertEquals(new OutputConfig(original).hashCode(), new OutputConfig(copy).hashCode());
        Assertions.assertEquals(new UnitConfig(original), new UnitConfig(copy));
        Assertions.assertEquals(new UnitConfig(original).hashCode(), new UnitConfig(copy).hashCode());
    }

    @Test
    void everyBusinessFieldParticipatesInEquality() {
        InputConfig base = InputConfig.DEFAULT;
        List<InputConfig> changed = Arrays.asList(
                InputConfig.builder(base).returnMode(ReturnMode.STRICT_IGNORE_NBT).build(),
                InputConfig.builder(base).blocking(!base.general().blocking()).build(),
                InputConfig.builder(base).productExtraction(!base.output().productExtraction()).build(),
                InputConfig.builder(base).productExtractionInterval((short) 7).build(),
                InputConfig.builder(base).productExtractionQuantity(4097).build(),
                InputConfig.builder(base).breakPickup(!base.unit().breakPickup()).build(),
                InputConfig.builder(base).outputPortMode(OutputPortMode.SINGLE).build(),
                InputConfig.builder(base).redstonePortMode(RedstoneMode.PULSE).build(),
                InputConfig.builder(base).redstonePortStrength((short) 3).build(),
                InputConfig.builder(base).redstonePortPulseWidth((short) 4).build(),
                InputConfig.builder(base).redstonePortPulsePeriod((short) 13).build(),
                InputConfig.builder(base).taskAllocationMode(TaskAllocationMode.PRIORITY).build(),
                InputConfig.builder(base).taskSplitMode(TaskSplitMode.BATCH).build());
        for (InputConfig config : changed) {
            Assertions.assertNotEquals(base, config);
            Assertions.assertEquals(config, InputConfig.builder(config).build());
        }
    }
}
