package cn.ae2bc.core.dispatch;

import java.util.function.IntPredicate;

/** Attempts candidates in round-robin order and returns the accepted index. */
public final class RoundRobinSelector {
    private RoundRobinSelector() {
    }

    public static int select(int cursor, int size, IntPredicate attempt) {
        if (attempt == null) {
            throw new NullPointerException("attempt");
        }
        if (size <= 0) {
            return -1;
        }
        for (int offset = 0; offset < size; offset++) {
            int index = RoundRobinPolicy.index(cursor, offset, size);
            if (attempt.test(index)) {
                return index;
            }
        }
        return -1;
    }
}
