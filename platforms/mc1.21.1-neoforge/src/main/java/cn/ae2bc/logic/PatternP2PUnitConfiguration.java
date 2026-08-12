package cn.ae2bc.logic;

import net.minecraft.nbt.CompoundTag;

/** Settings broadcast by the input tunnel or stored locally by a unit manager. */
public record PatternP2PUnitConfiguration(
        ReturnMode returnMode,
        boolean breakRecovery,
        int redstoneStrength,
        RedstoneOutputMode redstoneMode,
        int pulseWidthTicks,
        int pulsePeriodTicks,
        int productExtractionInterval,
        int productExtractionAmount) {
    public static final int DEFAULT_REDSTONE_STRENGTH = 15;
    public static final int DEFAULT_PULSE_WIDTH = 2;
    public static final int DEFAULT_PULSE_PERIOD = 20;
    public static final int MAX_PULSE_TICKS = 2000;
    public static final PatternP2PUnitConfiguration DEFAULT = new PatternP2PUnitConfiguration(
            ReturnMode.UNBLOCKED, true, DEFAULT_REDSTONE_STRENGTH,
            RedstoneOutputMode.SINGLE_TRIGGER, DEFAULT_PULSE_WIDTH, DEFAULT_PULSE_PERIOD,
            ProductExtractionSettings.DEFAULT_INTERVAL, ProductExtractionSettings.DEFAULT_AMOUNT);

    public PatternP2PUnitConfiguration {
        returnMode = returnMode == null ? ReturnMode.UNBLOCKED : returnMode;
        redstoneMode = redstoneMode == null ? RedstoneOutputMode.SINGLE_TRIGGER : redstoneMode;
        redstoneStrength = Math.clamp(redstoneStrength, 0, 15);
        pulsePeriodTicks = Math.clamp(pulsePeriodTicks, 1, MAX_PULSE_TICKS);
        pulseWidthTicks = Math.clamp(pulseWidthTicks, 1, pulsePeriodTicks);
        productExtractionInterval = ProductExtractionSettings.clampInterval(productExtractionInterval);
        productExtractionAmount = ProductExtractionSettings.clampAmount(productExtractionAmount);
    }

    public PatternP2PUnitConfiguration withReturnMode(ReturnMode value) {
        return new PatternP2PUnitConfiguration(value, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount);
    }

    public PatternP2PUnitConfiguration withBreakRecovery(boolean value) {
        return new PatternP2PUnitConfiguration(returnMode, value, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount);
    }

    public PatternP2PUnitConfiguration withRedstone(int strength, RedstoneOutputMode mode,
                                          int widthTicks, int periodTicks) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, strength, mode, widthTicks, periodTicks,
                productExtractionInterval, productExtractionAmount);
    }

    public PatternP2PUnitConfiguration withProductExtraction(int interval, int amount) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks, interval, amount);
    }

    public CompoundTag write() {
        CompoundTag data = new CompoundTag();
        data.putByte("ReturnMode", (byte) returnMode.getId());
        data.putBoolean("BreakRecovery", breakRecovery);
        data.putByte("RedstoneStrength", (byte) redstoneStrength);
        data.putByte("RedstoneMode", (byte) redstoneMode.getId());
        data.putInt("PulseWidth", pulseWidthTicks);
        data.putInt("PulsePeriod", pulsePeriodTicks);
        data.putInt("ProductExtractionInterval", productExtractionInterval);
        data.putInt("ProductExtractionAmount", productExtractionAmount);
        return data;
    }

    public static PatternP2PUnitConfiguration read(CompoundTag data) {
        if (data == null || data.isEmpty()) {
            return DEFAULT;
        }
        return new PatternP2PUnitConfiguration(
                data.contains("ReturnMode") ? ReturnMode.fromId(data.getByte("ReturnMode")) : DEFAULT.returnMode,
                !data.contains("BreakRecovery") || data.getBoolean("BreakRecovery"),
                data.contains("RedstoneStrength") ? data.getByte("RedstoneStrength") : DEFAULT.redstoneStrength,
                data.contains("RedstoneMode")
                        ? RedstoneOutputMode.fromId(data.getByte("RedstoneMode")) : DEFAULT.redstoneMode,
                data.contains("PulseWidth") ? data.getInt("PulseWidth") : DEFAULT.pulseWidthTicks,
                data.contains("PulsePeriod") ? data.getInt("PulsePeriod") : DEFAULT.pulsePeriodTicks,
                data.contains("ProductExtractionInterval")
                        ? data.getInt("ProductExtractionInterval") : DEFAULT.productExtractionInterval,
                data.contains("ProductExtractionAmount")
                        ? data.getInt("ProductExtractionAmount") : DEFAULT.productExtractionAmount);
    }
}
