package org.ae2bc.part.codec;

import net.minecraft.nbt.CompoundTag;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.RedstonePortParam;
import org.ae2bc.core.unit.OutputPortMode;

public class UnitPageAttrCodec {
    public static final String CONFIG_UNIT = "UnitConfig";
    private static final String CONFIG_UNIT_BREAK_PICKUP = "UnitBreakPickup";
    private static final String CONFIG_UNIT_OUTPUT_PORT_MODE = "UnitOutputPortMode";
    private static final String CONFIG_UNIT_REDSTONE_PORT_PARAM_REDSTONE_MODE = "UnitRedstonePortParamRedstoneMode";
    private static final String CONFIG_UNIT_REDSTONE_PORT_PARAM_STRENGTH = "UnitRedstonePortParamStrength";
    private static final String CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_WIDTH = "UnitRedstonePortParamPulseWidth";
    private static final String CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_PERIOD = "UnitRedstonePortParamPulsePeriod";

    public static void write(CompoundTag data, UnitPageConfig config) {
        data.putBoolean(CONFIG_UNIT_BREAK_PICKUP, config.breakPickup());
        data.putInt(CONFIG_UNIT_OUTPUT_PORT_MODE, config.outputPortMode().id);
        data.putInt(CONFIG_UNIT_REDSTONE_PORT_PARAM_REDSTONE_MODE, config.redstonePortParam().getMode().id);
        data.putShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_STRENGTH, config.redstonePortParam().getStrength());
        data.putShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_WIDTH, config.redstonePortParam().getPulseWidth());
        data.putShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_PERIOD, config.redstonePortParam().getPulsePeriod());
    }

    public static CompoundTag read(CompoundTag data) {
        return data.getCompound(CONFIG_UNIT);
    }

    public static boolean breakPickup(CompoundTag data) {
        return data.getBoolean(CONFIG_UNIT_BREAK_PICKUP);
    }

    public static OutputPortMode outputPortMode(CompoundTag data) {
        int id = data.getInt(CONFIG_UNIT_OUTPUT_PORT_MODE);
        return OutputPortMode.null2defaultOf(id);
    }

    public static RedstonePortParam redstonePortParam(CompoundTag data) {
        int id = data.getInt(CONFIG_UNIT_REDSTONE_PORT_PARAM_REDSTONE_MODE);
        short strength = data.getShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_STRENGTH);
        short pulseWidth = data.getShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_WIDTH);
        short pulsePeriod = data.getShort(CONFIG_UNIT_REDSTONE_PORT_PARAM_PULSE_PERIOD);
        return RedstonePortParam.of(RedstoneMode.null2defaultOf(id), strength, pulseWidth, pulsePeriod);
    }
}
