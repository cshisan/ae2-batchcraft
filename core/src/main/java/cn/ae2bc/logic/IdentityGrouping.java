package cn.ae2bc.logic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class IdentityGrouping {
    private IdentityGrouping() {
    }

    static <K, V> Map<V, Set<K>> invert(Map<K, V> source) {
        Map<V, Set<K>> result = new IdentityHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<K, V> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.computeIfAbsent(entry.getValue(), ignored ->
                        Collections.newSetFromMap(new IdentityHashMap<>())).add(entry.getKey());
            }
        }
        return result;
    }
}
