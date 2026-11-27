package org.ae2bc.part.codec;

import net.minecraft.nbt.CompoundTag;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.unit.ReturnMode;

public class GeneralPageAttrCodec {
    public static final String CONFIG_GENERAL = "GeneralConfig";
    private static final String CONFIG_GENERAL_RETURN_MODE = "GeneralReturnMode";
    private static final String CONFIG_GENERAL_EXTRACTION_INTERVAL = "GeneralExtractionInterval";
    private static final String CONFIG_GENERAL_EXTRACTION_QUANTITY = "GeneralExtractionQuantity";
    private static final String BLOCKING = "Blocking";

    public static void write(CompoundTag data, GeneralPageConfig config) {
        data.putInt(CONFIG_GENERAL_RETURN_MODE, config.returnMode().id);
        data.putShort(CONFIG_GENERAL_EXTRACTION_INTERVAL, config.extractionParam().getInterval());
        data.putInt(CONFIG_GENERAL_EXTRACTION_QUANTITY, config.extractionParam().getQuantity());
        data.putBoolean(GeneralPageAttrCodec.BLOCKING, config.blocking());
    }

    public static CompoundTag read(CompoundTag data) {
        return data.getCompound(CONFIG_GENERAL);
    }

    public static ReturnMode returnMode(CompoundTag data) {
        int id = data.getInt(CONFIG_GENERAL_RETURN_MODE);
        return ReturnMode.null2defaultOf(id);
    }

    public static boolean blocking(CompoundTag data) {
        return !data.contains(GeneralPageAttrCodec.BLOCKING) || data.getBoolean(GeneralPageAttrCodec.BLOCKING);
    }

    /* 缺失字段使用输入端默认值，不覆盖已保存的参数。 */
    public static ProductExtractionParam extractionParam(CompoundTag data) {
        ProductExtractionParam defaults = InputConfig.DEFAULT.general().extractionParam();
        short interval = data.contains(GeneralPageAttrCodec.CONFIG_GENERAL_EXTRACTION_INTERVAL)
                ? data.getShort(GeneralPageAttrCodec.CONFIG_GENERAL_EXTRACTION_INTERVAL) : defaults.getInterval();
        int quantity = data.contains(GeneralPageAttrCodec.CONFIG_GENERAL_EXTRACTION_QUANTITY)
                ? data.getInt(GeneralPageAttrCodec.CONFIG_GENERAL_EXTRACTION_QUANTITY) : defaults.getQuantity();
        return new ProductExtractionParam(interval, quantity);
    }
}
