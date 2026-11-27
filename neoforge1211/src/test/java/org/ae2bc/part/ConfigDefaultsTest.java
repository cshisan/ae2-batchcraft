package org.ae2bc.part;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.core.constant.Pp2mpOutputKeys;
import org.ae2bc.core.constant.Pp2mpUnitManagerKeys;
import org.ae2bc.core.constant.Pp2mpUnitPortKeys;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.RedstonePortParam;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.part.codec.GeneralPageAttrCodec;
import org.ae2bc.part.codec.OutputPageAttrCodec;
import org.ae2bc.part.codec.UnitPageAttrCodec;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {
    private final RegistryAccess registries =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private final Pp2mpInputData input = new Pp2mpInputData(() -> { }, () -> { });
    private final Pp2mpOutputData output = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
    private final Pp2mpUnitManagerData manager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
    private final Pp2mpUnitOutputTypePortData port = new Pp2mpUnitOutputTypePortData(() -> { }, () -> { });

    @Test
    void newDevicesUseInputDefaultsAlongBothChains() {
        Assertions.assertEquals(20, InputConfig.DEFAULT.unit().redstonePortParam().getPulsePeriod());
        this.assertChain(InputConfig.DEFAULT);
        this.assertSync(true);
        Assertions.assertEquals(0, this.port.getPriority());
    }

    @Test
    void emptyNbtUsesTheSameDefaultsAsNewDevices() {
        this.readNbt(new CompoundTag());
        this.assertChain(InputConfig.DEFAULT);
        this.assertSync(true);
        Assertions.assertEquals(0, this.port.getPriority());
    }

    @Test
    void everyMissingPageFieldUsesInputDefaults() {
        CompoundTag complete = this.writeNbt();
        for (String device : complete.getAllKeys()) {
            for (String section : new String[]{
                    GeneralPageAttrCodec.CONFIG_GENERAL, OutputPageAttrCodec.CONFIG_OUTPUT, UnitPageAttrCodec.CONFIG_UNIT
            }) {
                for (String key : complete.getCompound(device).getCompound(section).getAllKeys()) {
                    CompoundTag partial = complete.copy();
                    partial.getCompound(device).getCompound(section).remove(key);
                    this.readNbt(partial);
                    this.assertChain(InputConfig.DEFAULT);
                }
            }
        }
    }

    @Test
    void missingSyncFlagsRestoreEnabledButSavedFalseIsPreserved() {
        this.output.setConfigSyncSwitch(false);
        this.manager.setSyncEnabled(false);
        this.port.setSyncEnabled(false);
        CompoundTag disabled = this.writeNbt();
        this.readNbt(disabled);
        this.assertSync(false);
        disabled.getCompound("output").remove(Pp2mpOutputKeys.OUTPUT_SYNCED_ENABLED);
        disabled.getCompound("manager").remove(Pp2mpUnitManagerKeys.UNIT_MANAGER_SYNCED_ENABLED);
        disabled.getCompound("port").remove(Pp2mpUnitPortKeys.UNIT_PORT_SYNCED_ENABLED);
        this.readNbt(disabled);
        this.assertSync(true);
    }

    @Test
    void explicitSettingsRoundTripWithoutBeingReplacedByDefaults() {
        InputConfig expected = ConfigDefaultsTest.customConfig();
        this.configure(expected);
        this.output.setConfigSyncSwitch(false);
        this.manager.setSyncEnabled(false);
        this.port.setSyncEnabled(false);
        this.port.setPriority(-37);
        CompoundTag saved = this.writeNbt();
        this.readNbt(new CompoundTag());
        this.readNbt(saved);
        this.assertChain(expected);
        this.assertSync(false);
        Assertions.assertEquals(-37, this.port.getPriority());
        Assertions.assertEquals(expected, this.input.getConfig());
        Assertions.assertEquals(new OutputConfig(expected), this.output.getConfig());
        Assertions.assertEquals(new UnitConfig(expected), this.manager.getConfig());
    }

    @Test
    void savedTwoThousandTickPeriodIsNotReplacedByTheNewDefault() {
        InputConfig expected = InputConfig.builder(InputConfig.DEFAULT)
                .redstonePortPulsePeriod((short) 2000)
                .build();
        this.configure(expected);
        this.readNbt(this.writeNbt());
        this.assertChain(expected);
        Assertions.assertEquals(2000, this.input.getConfig().unit().redstonePortParam().getPulsePeriod());
        Assertions.assertEquals(2000, this.manager.getConfig().unit().redstonePortParam().getPulsePeriod());
    }

    @Test
    void partialNbtFillsOnlyMissingFields() {
        InputConfig custom = ConfigDefaultsTest.customConfig();
        this.configure(custom);
        CompoundTag partial = this.writeNbt();
        for (String device : new String[]{"input", "output", "manager"}) {
            CompoundTag deviceTag = partial.getCompound(device);
            deviceTag.getCompound(GeneralPageAttrCodec.CONFIG_GENERAL).remove("GeneralExtractionInterval");
            deviceTag.getCompound(UnitPageAttrCodec.CONFIG_UNIT).remove("UnitRedstonePortParamStrength");
            deviceTag.getCompound(UnitPageAttrCodec.CONFIG_UNIT).remove("UnitRedstonePortParamPulsePeriod");
        }
        this.readNbt(partial);
        RedstonePortParam defaults = InputConfig.DEFAULT.unit().redstonePortParam();
        InputConfig expected = InputConfig.builder(custom)
                .productExtractionInterval(InputConfig.DEFAULT.general().extractionParam().getInterval())
                .redstonePortStrength(defaults.getStrength())
                .redstonePortPulsePeriod(defaults.getPulsePeriod())
                .build();
        this.assertChain(expected);
    }

    @Test
    void defaultsAndCustomValuesSurviveBothNetworkChains() {
        for (InputConfig expected : new InputConfig[]{InputConfig.DEFAULT, ConfigDefaultsTest.customConfig()}) {
            this.configure(expected);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), this.registries, ConnectionType.NEOFORGE);
            try {
                this.input.writeToStream(buffer);
                this.output.writeToStream(buffer);
                this.manager.writeToStream(buffer);
                this.port.writeToStream(buffer);
                Pp2mpInputData clientInput = new Pp2mpInputData(() -> { }, () -> { });
                Pp2mpOutputData clientOutput = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
                Pp2mpUnitManagerData clientManager =
                        new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
                Pp2mpUnitOutputTypePortData clientPort =
                        new Pp2mpUnitOutputTypePortData(() -> { }, () -> { });
                clientInput.readFromStream(buffer);
                clientOutput.readFromStream(buffer);
                clientManager.readFromStream(buffer);
                clientPort.readFromStream(buffer);
                ConfigDefaultsTest.assertConfigs(
                        expected, clientInput.getConfig(), clientOutput.getConfig(), clientManager.getConfig());
                Assertions.assertEquals(expected.unit().outputPortMode(), clientPort.getOutputPortMode());
                Assertions.assertTrue(clientOutput.isConfigSyncSwitch());
                Assertions.assertTrue(clientManager.isSyncEnabled());
                Assertions.assertTrue(clientPort.isSyncEnabled());
                Assertions.assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    private void configure(InputConfig config) {
        this.input.update(config);
        this.output.apply(config);
        this.manager.apply(config);
        this.port.apply(this.manager.getConfig());
    }

    private CompoundTag writeNbt() {
        CompoundTag tag = new CompoundTag();
        CompoundTag inputTag = new CompoundTag();
        CompoundTag outputTag = new CompoundTag();
        CompoundTag managerTag = new CompoundTag();
        CompoundTag portTag = new CompoundTag();
        this.input.writeToNBT(inputTag, this.registries);
        this.output.writeToNBT(outputTag, this.registries);
        this.manager.writeToNBT(managerTag, this.registries);
        this.port.writeToNBT(portTag);
        tag.put("input", inputTag);
        tag.put("output", outputTag);
        tag.put("manager", managerTag);
        tag.put("port", portTag);
        return tag;
    }

    private void readNbt(CompoundTag tag) {
        this.input.readFromNBT(tag.getCompound("input"), this.registries);
        this.output.readFromNBT(tag.getCompound("output"), this.registries);
        this.manager.readFromNBT(tag.getCompound("manager"), this.registries);
        this.port.readFromNBT(tag.getCompound("port"));
    }

    private void assertChain(InputConfig expected) {
        ConfigDefaultsTest.assertConfigs(
                expected, this.input.getConfig(), this.output.getConfig(), this.manager.getConfig());
        Assertions.assertEquals(expected.unit().outputPortMode(), this.port.getOutputPortMode());
    }

    private void assertSync(boolean expected) {
        Assertions.assertEquals(expected, this.output.isConfigSyncSwitch());
        Assertions.assertEquals(expected, this.manager.isSyncEnabled());
        Assertions.assertEquals(expected, this.port.isSyncEnabled());
    }

    private static void assertConfigs(InputConfig expected, InputConfig input, OutputConfig output, UnitConfig manager) {
        for (GeneralPageConfig general : new GeneralPageConfig[]{input.general(), output.general(), manager.general()}) {
            Assertions.assertEquals(expected.general().returnMode(), general.returnMode());
            Assertions.assertEquals(expected.general().blocking(), general.blocking());
            Assertions.assertEquals(expected.general().extractionParam().getInterval(), general.extractionParam().getInterval());
            Assertions.assertEquals(expected.general().extractionParam().getQuantity(), general.extractionParam().getQuantity());
        }
        Assertions.assertEquals(expected.output().productExtraction(), input.output().productExtraction());
        Assertions.assertEquals(expected.output().productExtraction(), output.output().productExtraction());
        for (UnitPageConfig unit : new UnitPageConfig[]{input.unit(), manager.unit()}) {
            Assertions.assertEquals(expected.unit().breakPickup(), unit.breakPickup());
            Assertions.assertEquals(expected.unit().outputPortMode(), unit.outputPortMode());
            RedstonePortParam redstone = unit.redstonePortParam();
            RedstonePortParam expectedRedstone = expected.unit().redstonePortParam();
            Assertions.assertEquals(expectedRedstone.getMode(), redstone.getMode());
            Assertions.assertEquals(expectedRedstone.getStrength(), redstone.getStrength());
            Assertions.assertEquals(expectedRedstone.getPulseWidth(), redstone.getPulseWidth());
            Assertions.assertEquals(expectedRedstone.getPulsePeriod(), redstone.getPulsePeriod());
        }
        Assertions.assertEquals(expected.taskAllocationMode(), input.taskAllocationMode());
        Assertions.assertEquals(expected.taskSplitMode(), input.taskSplitMode());
    }

    private static InputConfig customConfig() {
        return InputConfig.builder(InputConfig.DEFAULT)
                .returnMode(ReturnMode.STRICT_IGNORE_NBT)
                .blocking(false)
                .productExtraction(true)
                .productExtractionInterval((short) 1)
                .productExtractionQuantity(4096)
                .breakPickup(false)
                .outputPortMode(OutputPortMode.SAME_TYPE)
                .redstonePortMode(RedstoneMode.PULSE)
                .redstonePortStrength((short) 7)
                .redstonePortPulseWidth((short) 3)
                .redstonePortPulsePeriod((short) 11)
                .taskAllocationMode(TaskAllocationMode.PRIORITY)
                .taskSplitMode(TaskSplitMode.BATCH)
                .build();
    }
}
