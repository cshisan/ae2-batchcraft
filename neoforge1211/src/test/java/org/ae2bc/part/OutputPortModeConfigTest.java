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
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.part.input.Pp2mpInputData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class OutputPortModeConfigTest {
    @Test
    void allModesRoundTripThroughInputManagerAndPortNbtAndStreams() {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (OutputPortMode mode : OutputPortMode.values()) {
            Pp2mpInputData input = new Pp2mpInputData(() -> { }, () -> { });
            Pp2mpUnitManagerData manager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
            Pp2mpUnitOutputTypePortData port = new Pp2mpUnitOutputTypePortData(() -> { }, () -> { });
            input.update(InputConfig.builder(input.getConfig()).outputPortMode(mode).build());
            manager.apply(input.getConfig());
            port.apply(manager.getConfig());
            port.setPriority(42);
            CompoundTag tag = new CompoundTag();
            input.writeToNBT(tag, registries);
            input.readFromNBT(tag, registries);
            manager.writeToNBT(tag, registries);
            manager.readFromNBT(tag, registries);
            port.writeToNBT(tag);
            port.readFromNBT(tag);
            Assertions.assertEquals(mode, input.getConfig().unit().outputPortMode());
            Assertions.assertEquals(mode, manager.getConfig().unit().outputPortMode());
            Assertions.assertEquals(mode, port.getOutputPortMode());
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
            try {
                input.writeToStream(buffer);
                manager.writeToStream(buffer);
                port.writeToStream(buffer);
                Pp2mpInputData clientInput = new Pp2mpInputData(() -> { }, () -> { });
                Pp2mpUnitManagerData clientManager = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
                Pp2mpUnitOutputTypePortData clientPort = new Pp2mpUnitOutputTypePortData(() -> { }, () -> { });
                clientInput.readFromStream(buffer);
                clientManager.readFromStream(buffer);
                clientPort.readFromStream(buffer);
                Assertions.assertEquals(mode, clientInput.getConfig().unit().outputPortMode());
                Assertions.assertEquals(mode, clientManager.getConfig().unit().outputPortMode());
                Assertions.assertEquals(mode, clientPort.getOutputPortMode());
                Assertions.assertEquals(42, clientPort.getPriority());
                Assertions.assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void unchangedSyncDoesNotSaveOrSendUpdatesAndLocalModeIsRespected() {
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        Pp2mpUnitOutputTypePortData port = new Pp2mpUnitOutputTypePortData(
                saves::incrementAndGet, updates::incrementAndGet);
        UnitConfig config = new UnitConfig(InputConfig.builder(InputConfig.DEFAULT)
                .outputPortMode(OutputPortMode.SINGLE).build());
        port.apply(config);
        for (int repeat = 0; repeat < 100; repeat++) {
            port.apply(config);
        }
        Assertions.assertEquals(1, saves.get());
        Assertions.assertEquals(1, updates.get());
        port.setSyncEnabled(false);
        port.setOutputPortMode(OutputPortMode.SAME_TYPE);
        port.apply(config);
        Assertions.assertEquals(OutputPortMode.SAME_TYPE, port.getOutputPortMode());
        port.setSyncEnabled(true);
        port.apply(config);
        Assertions.assertEquals(OutputPortMode.SINGLE, port.getOutputPortMode());
        Assertions.assertEquals(2, saves.get());
    }
}
