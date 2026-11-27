package org.ae2bc.part.codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.RedstonePortParam;
import org.ae2bc.core.unit.OutputPortMode;

public interface UnitPageAttrPacket {
    static void write(RegistryFriendlyByteBuf data, UnitPageConfig config) {
        data.writeBoolean(config.breakPickup());
        data.writeVarInt(config.outputPortMode().id);
        data.writeVarInt(config.redstonePortParam().getMode().id);
        data.writeInt(config.redstonePortParam().getStrength());
        data.writeInt(config.redstonePortParam().getPulsePeriod());
        data.writeInt(config.redstonePortParam().getPulseWidth());
    }

    static UnitPageConfig read(RegistryFriendlyByteBuf data) {
        boolean breakPickup = data.readBoolean();
        OutputPortMode outputPortMode = OutputPortMode.null2defaultOf(data.readVarInt());

        RedstoneMode redstoneMode = RedstoneMode.null2defaultOf(data.readVarInt());
        short redstoneStrength = (short) data.readInt();
        short redstonePulsePeriod = (short) data.readInt();
        short redstonePulseWidth = (short) data.readInt();

        return new UnitPageConfig(
                breakPickup, outputPortMode,
                RedstonePortParam.of(
                        redstoneMode, redstoneStrength,
                        redstonePulseWidth, redstonePulsePeriod
                )
        );
    }
}
