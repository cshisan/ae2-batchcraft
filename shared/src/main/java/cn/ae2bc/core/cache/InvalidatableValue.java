package cn.ae2bc.core.cache;

import java.util.function.Supplier;

/** Lazily builds a value and keeps it until an owning lifecycle explicitly invalidates it. */
public final class InvalidatableValue<T> {
    private boolean valid;
    private T value;

    public T get(Supplier<T> rebuild) {
        if (!valid) {
            value = rebuild.get();
            valid = true;
        }
        return value;
    }

    public void invalidate() {
        valid = false;
        value = null;
    }
}
