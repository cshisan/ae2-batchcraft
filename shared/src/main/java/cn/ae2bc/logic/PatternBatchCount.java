package cn.ae2bc.logic;

/** Validates the player-declared number of atomic units in a processing pattern. */
public final class PatternBatchCount {
    public static final long DEFAULT = 1;

    private PatternBatchCount() {
    }

    public static long maximum(long[] amounts) {
        if (amounts == null || amounts.length == 0) {
            return DEFAULT;
        }
        long result = 0;
        for (long amount : amounts) {
            if (amount <= 0) {
                continue;
            }
            result = result == 0 ? amount : gcd(result, amount);
        }
        return Math.max(DEFAULT, result);
    }

    public static Validation validate(long requested, long maximum) {
        long safeMaximum = Math.max(DEFAULT, maximum);
        if (requested < DEFAULT) {
            return new Validation(DEFAULT, Result.INVALID);
        }
        if (requested > safeMaximum) {
            return new Validation(safeMaximum, Result.ABOVE_MAXIMUM);
        }
        if (safeMaximum % requested != 0) {
            return new Validation(DEFAULT, Result.NOT_DIVISIBLE);
        }
        return new Validation(requested, Result.VALID);
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    public enum Result {
        VALID,
        INVALID,
        ABOVE_MAXIMUM,
        NOT_DIVISIBLE
    }

    public static final class Validation {
        private final long value;
        private final Result result;

        private Validation(long value, Result result) {
            this.value = value;
            this.result = result;
        }

        public long getValue() {
            return value;
        }

        public Result getResult() {
            return result;
        }
    }
}
