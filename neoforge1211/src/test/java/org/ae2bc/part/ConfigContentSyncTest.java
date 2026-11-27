package org.ae2bc.part;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Items;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.unit.manager.Pp2mpPortAssignment;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ConfigContentSyncTest {
    private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private final AtomicInteger inputSaves = new AtomicInteger();
    private final AtomicInteger outputSaves = new AtomicInteger();
    private final AtomicInteger managerSaves = new AtomicInteger();
    private final Pp2mpInputData input = new Pp2mpInputData(this.inputSaves::incrementAndGet, () -> { });
    private final Pp2mpOutputData output = new Pp2mpOutputData(this.outputSaves::incrementAndGet, () -> { }, () -> { });
    private final Pp2mpUnitManagerData manager =
            new Pp2mpUnitManagerData(Items.STONE, this.managerSaves::incrementAndGet, () -> { });

    @Test
    void sameValuesAndUnrelatedFieldsDoNotSaveDownstream() {
        this.input.eventSource().register(this.output);
        this.input.eventSource().register(this.manager);
        this.input.update(InputConfig.builder(InputConfig.DEFAULT).blocking(false).build());
        for (int repeat = 0; repeat < 100; repeat++) {
            this.input.update(InputConfig.builder(this.input.getConfig()).build());
        }
        Assertions.assertEquals(1, this.inputSaves.get());
        Assertions.assertEquals(1, this.outputSaves.get());
        Assertions.assertEquals(1, this.managerSaves.get());
        this.input.update(InputConfig.builder(this.input.getConfig()).outputPortMode(OutputPortMode.SINGLE).build());
        Assertions.assertEquals(1, this.outputSaves.get());
        Assertions.assertEquals(2, this.managerSaves.get());
        this.input.update(InputConfig.builder(this.input.getConfig()).productExtraction(true).build());
        Assertions.assertEquals(2, this.outputSaves.get());
        Assertions.assertEquals(2, this.managerSaves.get());
    }

    @Test
    void disablingSyncRetainsOverwrittenValuesAcrossSavingAndReenablingReplacesThem() {
        InputConfig first = InputConfig.builder(InputConfig.DEFAULT).blocking(false).productExtractionQuantity(4096).build();
        this.output.apply(first);
        this.manager.apply(first);
        this.output.setConfigSyncSwitch(false);
        this.manager.setSyncEnabled(false);
        this.output.apply(InputConfig.DEFAULT);
        this.manager.apply(InputConfig.DEFAULT);
        CompoundTag outputTag = new CompoundTag();
        CompoundTag managerTag = new CompoundTag();
        this.output.writeToNBT(outputTag, this.registries);
        this.manager.writeToNBT(managerTag, this.registries);
        this.output.readFromNBT(outputTag, this.registries);
        this.manager.readFromNBT(managerTag, this.registries);
        Assertions.assertEquals(new OutputConfig(first), this.output.getConfig());
        Assertions.assertEquals(new UnitConfig(first), this.manager.getConfig());
        Assertions.assertFalse(this.output.isConfigSyncSwitch());
        Assertions.assertFalse(this.manager.isSyncEnabled());
        this.output.setConfigSyncSwitch(true);
        this.manager.setSyncEnabled(true);
        this.output.apply(InputConfig.DEFAULT);
        this.manager.apply(InputConfig.DEFAULT);
        Assertions.assertEquals(OutputConfig.DEFAULT, this.output.getConfig());
        Assertions.assertEquals(UnitConfig.DEFAULT, this.manager.getConfig());
    }

    @Test
    void releasedConfigVersionIsIgnoredWhileAllSavedBusinessFieldsAreRetained() throws Exception {
        // 字段名、类型及层级来自 v2.0.0；不通过新写入器生成旧格式。
        CompoundTag old = TagParser.parseTag("""
                {ConfigVersion:65537,OutputSyncedEnabled:0b,UnitManagerSyncedEnabled:0b,
                 GeneralConfig:{GeneralReturnMode:1,GeneralExtractionInterval:1s,
                                GeneralExtractionQuantity:4096,Blocking:0b},
                 OutputConfig:{OutputProductExtraction:1b},
                 UnitConfig:{UnitBreakPickup:0b,UnitOutputPortMode:2,
                             UnitRedstonePortParamRedstoneMode:1,UnitRedstonePortParamStrength:7s,
                             UnitRedstonePortParamPulseWidth:3s,UnitRedstonePortParamPulsePeriod:2000s}}
                """);
        this.input.readFromNBT(old, this.registries);
        this.output.readFromNBT(old, this.registries);
        this.manager.readFromNBT(old, this.registries);
        InputConfig expected = this.input.getConfig();
        Assertions.assertFalse(expected.general().blocking());
        Assertions.assertEquals(4096, expected.general().extractionParam().getQuantity());
        Assertions.assertEquals(1, expected.general().extractionParam().getInterval());
        Assertions.assertTrue(expected.output().productExtraction());
        Assertions.assertFalse(expected.unit().breakPickup());
        Assertions.assertEquals(7, expected.unit().redstonePortParam().getStrength());
        Assertions.assertEquals(2000, expected.unit().redstonePortParam().getPulsePeriod());
        Assertions.assertEquals(new OutputConfig(expected), this.output.getConfig());
        Assertions.assertEquals(new UnitConfig(expected), this.manager.getConfig());
        old.putInt("ConfigVersion", Integer.MIN_VALUE);
        this.input.readFromNBT(old, this.registries);
        Assertions.assertEquals(expected, this.input.getConfig());
        CompoundTag saved = new CompoundTag();
        this.input.writeToNBT(saved, this.registries);
        this.output.writeToNBT(saved, this.registries);
        this.manager.writeToNBT(saved, this.registries);
        Assertions.assertFalse(saved.contains("ConfigVersion"));
    }

    @Test
    void applyingSettingsDoesNotResetTasksRecoveryOrEstablishedAssignments() {
        UUID taskId = UUID.randomUUID();
        AEItemKey stone = AEItemKey.of(Items.STONE);
        this.output.begin(Map.of(stone, 12L), null, taskId, null);
        this.manager.begin(Map.of(stone, 12L), null, taskId, null);
        this.output.getInputRecovery().put(stone, 3L);
        Pp2mpPortAssignment assignment = new Pp2mpPortAssignment(OutputPortMode.SINGLE);
        this.manager.getPortAssignments().put("port", assignment);
        InputConfig next = InputConfig.builder(InputConfig.DEFAULT).blocking(false)
                .outputPortMode(OutputPortMode.SAME_TYPE).build();
        this.output.apply(next);
        this.manager.apply(next);
        Assertions.assertTrue(this.output.isTaskActive());
        Assertions.assertTrue(this.manager.isTaskActive());
        Assertions.assertEquals(3L, this.output.getInputRecovery().get(stone));
        Assertions.assertSame(assignment, this.manager.getPortAssignments().get("port"));
    }
}
