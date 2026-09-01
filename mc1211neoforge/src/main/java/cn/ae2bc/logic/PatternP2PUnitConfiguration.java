package cn.ae2bc.logic;

import net.minecraft.nbt.CompoundTag;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;

/** Settings broadcast by the input tunnel or stored locally by a unit manager. */
public record PatternP2PUnitConfiguration(
        ReturnMode returnMode,
        boolean breakRecovery,
        int redstoneStrength,
        RedstoneOutputMode redstoneMode,
        int pulseWidthTicks,
        int pulsePeriodTicks,
        int productExtractionInterval,
        int productExtractionAmount,
        TransferPortOutputMode transferPortOutputMode,
        OutputSlotSharingMode outputSlotSharingMode) {
    public static final int DEFAULT_REDSTONE_STRENGTH = PatternP2PUnitSettings.DEFAULT_REDSTONE_STRENGTH;
    public static final int DEFAULT_PULSE_WIDTH = PatternP2PUnitSettings.DEFAULT_PULSE_WIDTH;
    public static final int DEFAULT_PULSE_PERIOD = PatternP2PUnitSettings.DEFAULT_PULSE_PERIOD;
    public static final int MAX_PULSE_TICKS = PatternP2PUnitSettings.MAX_PULSE_TICKS;
    public static final PatternP2PUnitConfiguration DEFAULT = new PatternP2PUnitConfiguration(
            ReturnMode.UNBLOCKED, true, DEFAULT_REDSTONE_STRENGTH,
            RedstoneOutputMode.SINGLE_TRIGGER, DEFAULT_PULSE_WIDTH, DEFAULT_PULSE_PERIOD,
            ProductExtractionSettings.DEFAULT_INTERVAL, ProductExtractionSettings.DEFAULT_AMOUNT,
            TransferPortOutputMode.NORMAL, OutputSlotSharingMode.DISABLED);

    public PatternP2PUnitConfiguration(ReturnMode returnMode, boolean breakRecovery, int redstoneStrength,
            RedstoneOutputMode redstoneMode, int pulseWidthTicks, int pulsePeriodTicks,
            int productExtractionInterval, int productExtractionAmount) {
        this(returnMode, breakRecovery, redstoneStrength, redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount, TransferPortOutputMode.NORMAL,
                OutputSlotSharingMode.DISABLED);
    }

    public PatternP2PUnitConfiguration {
        returnMode = returnMode == null ? ReturnMode.UNBLOCKED : returnMode;
        redstoneMode = redstoneMode == null ? RedstoneOutputMode.SINGLE_TRIGGER : redstoneMode;
        redstoneStrength = Math.clamp(redstoneStrength, 0, 15);
        pulsePeriodTicks = Math.clamp(pulsePeriodTicks, 1, MAX_PULSE_TICKS);
        pulseWidthTicks = Math.clamp(pulseWidthTicks, 1, pulsePeriodTicks);
        productExtractionInterval = ProductExtractionSettings.clampInterval(productExtractionInterval);
        productExtractionAmount = ProductExtractionSettings.clampAmount(productExtractionAmount);
        transferPortOutputMode = transferPortOutputMode == null ? TransferPortOutputMode.NORMAL : transferPortOutputMode;
        outputSlotSharingMode = outputSlotSharingMode == null ? OutputSlotSharingMode.DISABLED : outputSlotSharingMode;
    }

    public PatternP2PUnitConfiguration withReturnMode(ReturnMode value) {
        return new PatternP2PUnitConfiguration(value, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount, transferPortOutputMode, outputSlotSharingMode);
    }

    public PatternP2PUnitConfiguration withBreakRecovery(boolean value) {
        return new PatternP2PUnitConfiguration(returnMode, value, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount, transferPortOutputMode, outputSlotSharingMode);
    }

    public PatternP2PUnitConfiguration withRedstone(int strength, RedstoneOutputMode mode,
                                          int widthTicks, int periodTicks) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, strength, mode, widthTicks, periodTicks,
                productExtractionInterval, productExtractionAmount, transferPortOutputMode, outputSlotSharingMode);
    }

    public PatternP2PUnitConfiguration withProductExtraction(int interval, int amount) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks, interval, amount, transferPortOutputMode,
                outputSlotSharingMode);
    }

    public PatternP2PUnitConfiguration withTransferPortOutputMode(TransferPortOutputMode value) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount, value, outputSlotSharingMode);
    }

    public PatternP2PUnitConfiguration withOutputSlotSharingMode(OutputSlotSharingMode value) {
        return new PatternP2PUnitConfiguration(returnMode, breakRecovery, redstoneStrength,
                redstoneMode, pulseWidthTicks, pulsePeriodTicks,
                productExtractionInterval, productExtractionAmount, transferPortOutputMode, value);
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
        data.putByte("TransferPortOutputMode", (byte) transferPortOutputMode.getId());
        data.putByte("OutputSlotSharingMode", (byte) outputSlotSharingMode.getId());
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
                        ? data.getInt("ProductExtractionAmount") : DEFAULT.productExtractionAmount,
                data.contains("TransferPortOutputMode")
                        ? TransferPortOutputMode.fromId(data.getByte("TransferPortOutputMode"))
                        : DEFAULT.transferPortOutputMode,
                data.contains("OutputSlotSharingMode")
                        ? OutputSlotSharingMode.fromId(data.getByte("OutputSlotSharingMode"))
                        : DEFAULT.outputSlotSharingMode);
    }
}
