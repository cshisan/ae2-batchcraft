package cn.ae2bc.pattern;

import java.util.Objects;

/** Marks the AE2 encoding logic that is currently producing a pattern result. */
public final class MaterialOutputEncodingContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private MaterialOutputEncodingContext() {
    }

    public static Scope enter(Object logic) {
        Objects.requireNonNull(logic, "logic");
        State previous = CURRENT.get();
        CURRENT.set(new State(logic));
        return new Scope(previous);
    }

    public static boolean isActiveFor(Object logic) {
        State state = CURRENT.get();
        return state != null && state.logic == logic;
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private boolean closed;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private static final class State {
        private final Object logic;

        private State(Object logic) {
            this.logic = logic;
        }
    }
}
