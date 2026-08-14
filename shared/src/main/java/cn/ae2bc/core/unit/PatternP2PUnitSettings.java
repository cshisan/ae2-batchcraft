package cn.ae2bc.core.unit;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;

/** Java 8 compatible, platform-independent settings for a Pattern P2P unit manager. */
public final class PatternP2PUnitSettings {
    public static final int DEFAULT_REDSTONE_STRENGTH = 15;
    public static final int DEFAULT_PULSE_WIDTH = 2;
    public static final int DEFAULT_PULSE_PERIOD = 20;
    public static final int MAX_PULSE_TICKS = 2000;
    public static final PatternP2PUnitSettings DEFAULT = new PatternP2PUnitSettings(
            ReturnMode.UNBLOCKED, true, ProductExtractionLimits.DEFAULT_INTERVAL,
            ProductExtractionLimits.DEFAULT_AMOUNT, RedstoneOutputMode.SINGLE_TRIGGER,
            DEFAULT_REDSTONE_STRENGTH, DEFAULT_PULSE_WIDTH, DEFAULT_PULSE_PERIOD);

    private final ReturnMode returnMode;
    private final boolean breakRecovery;
    private final int extractionInterval;
    private final int extractionAmount;
    private final RedstoneOutputMode redstoneMode;
    private final int redstoneStrength;
    private final int pulseWidthTicks;
    private final int pulsePeriodTicks;

    public PatternP2PUnitSettings(ReturnMode returnMode, boolean breakRecovery,
            int extractionInterval, int extractionAmount, RedstoneOutputMode redstoneMode,
            int redstoneStrength, int pulseWidthTicks, int pulsePeriodTicks) {
        this.returnMode = returnMode == null ? ReturnMode.UNBLOCKED : returnMode;
        this.breakRecovery = breakRecovery;
        this.extractionInterval = ProductExtractionLimits.clampInterval(extractionInterval);
        this.extractionAmount = ProductExtractionLimits.clampAmount(extractionAmount);
        this.redstoneMode = redstoneMode == null ? RedstoneOutputMode.SINGLE_TRIGGER : redstoneMode;
        this.redstoneStrength = clamp(redstoneStrength, 0, 15);
        this.pulsePeriodTicks = clamp(pulsePeriodTicks, 1, MAX_PULSE_TICKS);
        this.pulseWidthTicks = clamp(pulseWidthTicks, 1, this.pulsePeriodTicks);
    }

    public ReturnMode getReturnMode() { return returnMode; }
    public boolean isBreakRecovery() { return breakRecovery; }
    public int getExtractionInterval() { return extractionInterval; }
    public int getExtractionAmount() { return extractionAmount; }
    public RedstoneOutputMode getRedstoneMode() { return redstoneMode; }
    public int getRedstoneStrength() { return redstoneStrength; }
    public int getPulseWidthTicks() { return pulseWidthTicks; }
    public int getPulsePeriodTicks() { return pulsePeriodTicks; }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
