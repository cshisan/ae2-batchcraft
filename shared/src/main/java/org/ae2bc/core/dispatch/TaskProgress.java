package org.ae2bc.core.dispatch;

import java.util.LinkedHashMap;
import java.util.Map;

/** 返回范围不随完成计数递减，避免超额产物被过滤。 */
public final class TaskProgress<K> {
    private final Map<K, Long> declared = new LinkedHashMap<>();
    private final Map<K, Long> remaining = new LinkedHashMap<>();

    public Map<K, Long> declared() {
        return this.declared;
    }

    public Map<K, Long> remaining() {
        return this.remaining;
    }

    public void begin(Map<K, Long> outputs) {
        this.clear();
        this.declared.putAll(outputs);
        this.remaining.putAll(outputs);
    }

    /* 仅扣减匹配的完成要求，不限制实际接收数量。 */
    public boolean record(K key, long amount) {
        Long required = this.remaining.get(key);
        if (required == null || amount <= 0) {
            return false;
        }
        if (amount >= required) {
            this.remaining.remove(key);
        } else {
            this.remaining.put(key, required - amount);
        }
        return true;
    }

    public boolean pending(K primary) {
        return primary == null ? !this.remaining.isEmpty() : this.remaining.getOrDefault(primary, 0L) > 0;
    }

    public void clear() {
        this.declared.clear();
        this.remaining.clear();
    }
}
