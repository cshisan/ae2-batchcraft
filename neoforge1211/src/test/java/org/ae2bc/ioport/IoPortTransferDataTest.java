package org.ae2bc.ioport;

import appeng.api.orientation.RelativeSide;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.core.ioport.CellTransferMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IoPortTransferDataTest {
    @Test
    void defaultsAndUnknownStoredModeUseOriginalSlotsBehavior() {
        Assertions.assertEquals(IoPortTransferData.DEFAULT, IoPortTransferData.load(new CompoundTag()));
        Assertions.assertEquals(CellTransferMode.SLOTS, CellTransferMode.fromId("unknown"));
        Assertions.assertEquals(63, new IoPortTransferData(CellTransferMode.EXTERNAL, -1).sides());
    }

    @Test
    void modeCyclesInRequestedOrderAndPreservesSelectedSides() {
        IoPortTransferData data = IoPortTransferData.DEFAULT.toggle(RelativeSide.LEFT).toggle(RelativeSide.TOP);
        for (CellTransferMode mode : CellTransferMode.values()) {
            Assertions.assertEquals(mode, data.mode());
            Assertions.assertTrue(data.enabled(RelativeSide.LEFT));
            Assertions.assertTrue(data.enabled(RelativeSide.TOP));
            data = data.nextMode();
        }
        Assertions.assertEquals(CellTransferMode.SLOTS, data.mode());
        Assertions.assertFalse(data.toggle(RelativeSide.LEFT).enabled(RelativeSide.LEFT));
        Assertions.assertEquals(IoPortTransferData.DEFAULT, data.clearSides());
    }

    /* 穷举四种模式及 64 种选面，验证存档和网络包不丢字段、不残留字节。 */
    @Test
    void allConfigurationsRoundTripThroughNbtAndPacket() {
        for (CellTransferMode mode : CellTransferMode.values()) {
            for (int sides = 0; sides < 64; sides++) {
                IoPortTransferData data = new IoPortTransferData(mode, sides);
                CompoundTag tag = new CompoundTag();
                tag.putInt("untouched", 12);
                data.save(tag);
                Assertions.assertEquals(12, tag.getInt("untouched"));
                Assertions.assertEquals(data, IoPortTransferData.load(tag));
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                        Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
                try {
                    IoPortTransferPacket packet = new IoPortTransferPacket(127, data, FullnessMode.FULL, OperationMode.FILL);
                    IoPortTransferPacket.STREAM_CODEC.encode(buffer, packet);
                    Assertions.assertEquals(packet, IoPortTransferPacket.STREAM_CODEC.decode(buffer));
                    Assertions.assertEquals(0, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
