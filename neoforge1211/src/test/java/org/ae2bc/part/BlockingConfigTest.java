package org.ae2bc.part;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockingConfigTest {
    @Test
    void localTogglesChangeContentAndSyncRejectsLocalChanges() {
        InputConfig input = InputConfig.builder(InputConfig.DEFAULT).blocking(false).build();
        Pp2mpOutputData output = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        Pp2mpUnitManagerData manager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        output.apply(input);
        manager.apply(input);
        OutputConfig oldOutput = output.getConfig();
        UnitConfig oldManager = manager.getConfig();
        output.update(new OutputConfig(oldOutput.general().withBlocking(true), oldOutput.output()));
        manager.update(new UnitConfig(oldManager.general().withBlocking(true), oldManager.unit()));
        Assertions.assertSame(oldOutput, output.getConfig());
        Assertions.assertSame(oldManager, manager.getConfig());
        output.setConfigSyncSwitch(false);
        manager.setSyncEnabled(false);
        for (boolean enabled : new boolean[]{true, false, true}) {
            OutputConfig previousOutput = output.getConfig();
            UnitConfig previousManager = manager.getConfig();
            output.update(new OutputConfig(
                    previousOutput.general().withBlocking(enabled), previousOutput.output()));
            manager.update(new UnitConfig(
                    previousManager.general().withBlocking(enabled), previousManager.unit()));
            Assertions.assertEquals(enabled, output.getConfig().general().blocking());
            Assertions.assertEquals(enabled, manager.getConfig().general().blocking());
            Assertions.assertNotEquals(previousOutput, output.getConfig());
            Assertions.assertNotEquals(previousManager, manager.getConfig());
        }
        output.setConfigSyncSwitch(true);
        manager.setSyncEnabled(true);
        output.apply(input);
        manager.apply(input);
        Assertions.assertFalse(output.getConfig().general().blocking());
        Assertions.assertFalse(manager.getConfig().general().blocking());
    }

    @Test
    void defaultLegacyNbtAndAllThreeStreamsPreserveBlocking() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Pp2mpInputData input = new Pp2mpInputData(() -> { }, () -> { });
        Pp2mpOutputData output = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
        Pp2mpUnitManagerData manager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        input.readFromNBT(new CompoundTag(), registries);
        output.readFromNBT(new CompoundTag(), registries);
        manager.readFromNBT(new CompoundTag(), registries);
        Assertions.assertTrue(input.getConfig().general().blocking());
        Assertions.assertTrue(output.getConfig().general().blocking());
        Assertions.assertTrue(manager.getConfig().general().blocking());
        output.setConfigSyncSwitch(true);
        manager.setSyncEnabled(true);
        input.update(InputConfig.builder(input.getConfig()).blocking(false).build());
        output.apply(input.getConfig());
        manager.apply(input.getConfig());

        CompoundTag tag = new CompoundTag();
        input.writeToNBT(tag, registries);
        input.readFromNBT(tag, registries);
        output.writeToNBT(tag, registries);
        output.readFromNBT(tag, registries);
        manager.writeToNBT(tag, registries);
        manager.readFromNBT(tag, registries);
        Assertions.assertFalse(input.getConfig().general().blocking());
        Assertions.assertFalse(output.getConfig().general().blocking());
        Assertions.assertFalse(manager.getConfig().general().blocking());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
        try {
            input.writeToStream(buffer);
            output.writeToStream(buffer);
            manager.writeToStream(buffer);
            Pp2mpInputData clientInput = new Pp2mpInputData(() -> { }, () -> { });
            Pp2mpOutputData clientOutput = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
            Pp2mpUnitManagerData clientManager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
            clientInput.readFromStream(buffer);
            clientOutput.readFromStream(buffer);
            clientManager.readFromStream(buffer);
            Assertions.assertFalse(clientInput.getConfig().general().blocking());
            Assertions.assertFalse(clientOutput.getConfig().general().blocking());
            Assertions.assertFalse(clientManager.getConfig().general().blocking());
            Assertions.assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
