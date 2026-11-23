package org.ae2bc.common;

public class Utils {
    public static <T> T null2default(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static long saturatingMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public static int operationsFor(long amount, long amountPerOperation) {
        if (amount <= 0 || amountPerOperation <= 0) {
            return 0;
        }
        long operations = (amount - 1) / amountPerOperation + 1;
        return (int) Math.min(Integer.MAX_VALUE, operations);
    }
}
