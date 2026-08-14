package cn.ae2bc.logic;

/** Numeric helpers shared by all supported Java and Minecraft versions. */
public final class Numbers {
    private Numbers() {
    }

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
