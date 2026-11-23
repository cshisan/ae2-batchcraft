package org.ae2bc.placer.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.placer.Pos;

public interface RangeAttrPacket {
    static void write(RegistryFriendlyByteBuf data, Pos range) {
        data.writeVarInt(range.x());
        data.writeVarInt(range.y());
        data.writeVarInt(range.z());
    }

    static Pos read(RegistryFriendlyByteBuf data) {
        return new Pos(data.readVarInt(), data.readVarInt(), data.readVarInt());
    }
}
