package cn.ae2bc.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

final class BoundedLruCache<K, V> {
    private final int capacity;
    private final Map<K, V> entries;

    BoundedLruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedLruCache.this.capacity;
            }
        };
    }

    V computeIfAbsent(K key, Function<? super K, ? extends V> factory) {
        return entries.computeIfAbsent(key, factory);
    }

    V get(K key) {
        return entries.get(key);
    }

    void put(K key, V value) {
        entries.put(key, value);
    }

    void remove(K key) {
        entries.remove(key);
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }
}
