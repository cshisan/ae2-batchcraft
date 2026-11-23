package org.ae2bc.part.codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.unit.ReturnMode;

public interface GeneralPageAttrPacket {
    static void write(RegistryFriendlyByteBuf data, GeneralPageConfig config) {
        data.writeVarInt(config.returnMode().id);
        data.writeShort(config.extractionParam().getInterval());
        data.writeInt(config.extractionParam().getQuantity());
        data.writeBoolean(config.blocking());
    }

    static GeneralPageConfig read(RegistryFriendlyByteBuf data) {
        ReturnMode returnMode = ReturnMode.null2defaultOf(data.readVarInt());
        short interval = data.readShort();
        int quantity = data.readInt();

        ProductExtractionParam extractionParam = ProductExtractionParam.of(interval, quantity);
        return new GeneralPageConfig(returnMode, extractionParam, data.readBoolean());
    }
}
