package org.ae2bc.core.unit;


import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 红石参数
 */
@EqualsAndHashCode
public final class RedstonePortParam {
    private final @Getter RedstoneMode mode;
    private final @Getter short strength;
    private final @Getter short pulseWidth;
    private final @Getter short pulsePeriod;

    public RedstonePortParam(RedstoneMode mode, short strength, short pulseWidth, short pulsePeriod) {
        this.mode = mode;
        this.strength = strength;
        this.pulsePeriod = pulsePeriod;
        this.pulseWidth = pulseWidth;
    }

    public static RedstonePortParam of(RedstoneMode mode, short strength, short pulseWidth, short pulsePeriod) {
        return new RedstonePortParam(mode, strength, pulseWidth > pulsePeriod ? pulsePeriod : pulseWidth, pulsePeriod);
    }

    /* 按单笔任务的运行时间计算强度，多任务由管理器取最大值。 */
    public int powerAt(long activeTicks) {
        long ticks = Math.max(0, activeTicks);
        if (this.mode == RedstoneMode.CONTINUOUS) {
            return this.strength;
        }
        if (this.mode == RedstoneMode.PULSE) {
            return ticks % Math.max(1, this.pulsePeriod) < this.pulseWidth ? this.strength : 0;
        }
        return ticks < this.pulseWidth ? this.strength : 0;
    }
}
