package org.ae2bc.part.codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.part.OutputPageConfig;

public interface OutputPageAttrPacket {
    static void write(RegistryFriendlyByteBuf data, OutputPageConfig config) {
        data.writeBoolean(config.productExtraction());
    }

    static OutputPageConfig read(RegistryFriendlyByteBuf data) {
        return new OutputPageConfig(data.readBoolean());
    }
}
