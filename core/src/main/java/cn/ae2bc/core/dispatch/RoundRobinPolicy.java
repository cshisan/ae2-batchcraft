package cn.ae2bc.core.dispatch;

/** Version-neutral round-robin arithmetic used by every platform adapter. */
public final class RoundRobinPolicy {
    private RoundRobinPolicy() {
    }

    public static int index(int cursor, int offset, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return (int) Math.floorMod((long) cursor + offset, size);
    }

    public static int advance(int successfulIndex, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return Math.floorMod(successfulIndex + 1, size);
    }
}
