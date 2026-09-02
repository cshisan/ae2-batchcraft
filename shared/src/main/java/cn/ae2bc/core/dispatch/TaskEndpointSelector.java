package cn.ae2bc.core.dispatch;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Selects one task endpoint without retaining mutable selection state. */
public final class TaskEndpointSelector {
    private TaskEndpointSelector() {
    }

    public static int select(TaskAllocationMode mode, int cursor, int size,
                             IntUnaryOperator boundedRandom, IntPredicate attempt) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attempt, "attempt");
        if (size <= 0) {
            return -1;
        }
        switch (mode) {
            case ROUND_ROBIN:
                return RoundRobinSelector.select(cursor, size, attempt);
            case PRIORITY:
                for (int index = 0; index < size; index++) {
                    if (attempt.test(index)) {
                        return index;
                    }
                }
                return -1;
            case RANDOM:
                Objects.requireNonNull(boundedRandom, "boundedRandom");
                int index = boundedRandom.applyAsInt(size);
                if (index < 0 || index >= size) {
                    throw new IllegalArgumentException("random index outside bound: " + index);
                }
                return attempt.test(index) ? index : -1;
            default:
                throw new IllegalStateException("Unhandled task allocation mode: " + mode);
        }
    }
}
