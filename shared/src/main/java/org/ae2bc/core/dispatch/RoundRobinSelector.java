package org.ae2bc.core.dispatch;

import java.util.function.IntPredicate;

/** 按轮询顺序尝试候选端点，并返回接受任务的索引。 */
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
