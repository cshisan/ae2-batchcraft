package org.ae2bc.part.codec;

import net.minecraft.nbt.CompoundTag;
import org.ae2bc.core.part.OutputPageConfig;

public class OutputPageAttrCodec {
    public static final String CONFIG_OUTPUT = "OutputConfig";
    private static final String CONFIG_OUTPUT_PRODUCT_EXTRACTION = "OutputProductExtraction";

    public static void write(CompoundTag data, OutputPageConfig config) {
        data.putBoolean(CONFIG_OUTPUT_PRODUCT_EXTRACTION, config.productExtraction());
    }

    public static CompoundTag read(CompoundTag data) {
        return data.getCompound(CONFIG_OUTPUT);
    }

    public static boolean productExtraction(CompoundTag data) {
        return data.getBoolean(CONFIG_OUTPUT_PRODUCT_EXTRACTION);
    }
}
