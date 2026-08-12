package cn.ae2bc.core.cache;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/** Rebuilds an immutable-key lookup at most once for each absolute game tick. */
public final class TickCachedIndex<K, V> {
    private long cachedTick = Long.MIN_VALUE;
    private Map<K, V> values = Collections.emptyMap();

    public V find(long tick, K key, Supplier<Map<K, V>> rebuild) {
        return values(tick, rebuild).get(key);
    }

    public Map<K, V> values(long tick, Supplier<Map<K, V>> rebuild) {
        if (cachedTick != tick) {
            Map<K, V> next = rebuild.get();
            values = next == null ? Collections.<K, V>emptyMap() : next;
            cachedTick = tick;
        }
        return values;
    }

    public void invalidate() {
        cachedTick = Long.MIN_VALUE;
        values = Collections.emptyMap();
    }
}
