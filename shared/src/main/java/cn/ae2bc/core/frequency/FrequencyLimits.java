package cn.ae2bc.core.frequency;

/** Shared unsigned-short frequency bounds used by every platform adapter. */
public final class FrequencyLimits {
    public static final int MIN = 0;
    public static final int MAX = 0xFFFF;

    private FrequencyLimits() { }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
