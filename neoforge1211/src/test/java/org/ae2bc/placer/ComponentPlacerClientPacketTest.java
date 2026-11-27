package org.ae2bc.placer;

import appeng.core.localization.GuiText;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.core.placer.Pos;
import org.ae2bc.core.placer.SelectionStatus;
import org.ae2bc.placer.packet.ComponentPlacerClientPacket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class ComponentPlacerClientPacketTest {
    @Test
    void everyDirectionAndSelectionRoundTripsWithDistinctCoordinates() {
        for (Direction direction : Direction.values()) {
            for (SelectionStatus selection : SelectionStatus.values()) {
                DataModel model = new DataModel(true, (short) 53000, (short) 27000,
                        new Pos(-16, 7, 13), new Pos(21, 18, 32), selection, true, false, true, false);
                for (Component reason : Arrays.asList(null, GuiText.OutOfPower.text())) {
                    ComponentPlacerClientPacket expected = new ComponentPlacerClientPacket(
                            19, direction, model, reason != null, reason);
                    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                            Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
                    try {
                        ComponentPlacerClientPacket.STREAM_CODEC.encode(buffer, expected);
                        Assertions.assertEquals(expected, ComponentPlacerClientPacket.STREAM_CODEC.decode(buffer));
                        Assertions.assertEquals(0, buffer.readableBytes());
                    } finally {
                        buffer.release();
                    }
                }
            }
        }
    }

    @Test
    void equivalentSnapshotsCompareByValueAndConnectionChangesDiffer() {
        DataModel first = new DataModel.Builder(DataModel.DEFAULT).pos1(new Pos(0, 0, 0)).build();
        Assertions.assertEquals(DataModel.DEFAULT, first);
        Assertions.assertEquals(DataModel.DEFAULT.hashCode(), first.hashCode());
        Assertions.assertNotEquals(first, new DataModel.Builder(first).aeConnected(true).build());
    }

    @Test
    void pairingAndReasonChangesInvalidateTheSnapshotEvenWhileDisconnected() {
        ComponentPlacerClientPacket original = new ComponentPlacerClientPacket(
                19, Direction.UP, DataModel.DEFAULT, true, GuiText.OutOfPower.text());
        Assertions.assertEquals(original, new ComponentPlacerClientPacket(
                19, Direction.UP, DataModel.DEFAULT, true, GuiText.OutOfPower.text()));
        Assertions.assertNotEquals(original, new ComponentPlacerClientPacket(
                19, Direction.UP, DataModel.DEFAULT, false, GuiText.OutOfPower.text()));
        Assertions.assertNotEquals(original, new ComponentPlacerClientPacket(
                19, Direction.UP, DataModel.DEFAULT, true, Component.translatable("other.reason")));
        Assertions.assertNotEquals(original, new ComponentPlacerClientPacket(
                19, Direction.UP, DataModel.DEFAULT, true, null));
    }
}
