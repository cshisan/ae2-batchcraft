package cn.ae2bc.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks one batch of concurrently accepted tasks that use the same pattern. */
public final class ReturnBatchTracker<K, P> {
    public static final int MAX_TASKS = 64;

    private P pattern;
    private int taskCount;
    private final Map<K, Long> declaredOutputs = new LinkedHashMap<>();
    private K primaryKey;
    private long expectedPrimary;

    public boolean isActive() {
        return taskCount > 0;
    }

    public boolean canPotentiallyAccept() {
        return !isActive() || pattern != null && taskCount < MAX_TASKS;
    }

    public boolean canAccept(P candidatePattern, Map<K, Long> outputs,
                             K candidatePrimaryKey, long candidatePrimaryAmount) {
        if (!isActive()) {
            return canAdd(outputs, candidatePrimaryKey, candidatePrimaryAmount);
        }
        return pattern != null
                && pattern.equals(candidatePattern)
                && taskCount < MAX_TASKS
                && declaredOutputs.equals(outputs)
                && Objects.equals(primaryKey, candidatePrimaryKey)
                && canAdd(outputs, candidatePrimaryKey, candidatePrimaryAmount);
    }

    public boolean begin(P candidatePattern, Map<K, Long> outputs,
                         K candidatePrimaryKey, long candidatePrimaryAmount) {
        if (!canAccept(candidatePattern, outputs, candidatePrimaryKey, candidatePrimaryAmount)) {
            return false;
        }
        if (!isActive()) {
            pattern = candidatePattern;
            taskCount = 0;
            declaredOutputs.clear();
            declaredOutputs.putAll(outputs);
            primaryKey = candidatePrimaryKey;
            expectedPrimary = 0;
        }
        expectedPrimary += candidatePrimaryAmount;
        taskCount++;
        return true;
    }

    public void rollback(long primaryAmount) {
        if (!isActive()) {
            return;
        }
        expectedPrimary = Math.max(0, expectedPrimary - primaryAmount);
        taskCount--;
        if (taskCount <= 0) {
            clear();
        }
    }

    public long filter(K key, long amount, ReturnMode configuredMode) {
        if (amount <= 0) {
            return 0;
        }
        if (!isActive() || configuredMode == ReturnMode.UNBLOCKED) {
            return amount;
        }
        return declaredOutputs.containsKey(key) ? amount : 0;
    }

    public boolean returned(K key, long amount) {
        if (!isActive() || amount <= 0) {
            return false;
        }
        if (Objects.equals(primaryKey, key)) {
            expectedPrimary = Math.max(0, expectedPrimary - amount);
        }
        boolean completed = expectedPrimary <= 0;
        if (completed) {
            clear();
        }
        return completed;
    }

    public void load(P loadedPattern, int loadedTaskCount,
                     Map<K, Long> loadedDeclaredOutputs, K loadedPrimaryKey, long loadedExpectedPrimary) {
        clear();
        if (loadedTaskCount <= 0
                || loadedDeclaredOutputs.isEmpty() || loadedPrimaryKey == null || loadedExpectedPrimary <= 0) {
            return;
        }
        pattern = loadedPattern;
        taskCount = Math.min(loadedTaskCount, MAX_TASKS);
        declaredOutputs.putAll(loadedDeclaredOutputs);
        primaryKey = loadedPrimaryKey;
        expectedPrimary = loadedExpectedPrimary;
    }

    public void clear() {
        pattern = null;
        taskCount = 0;
        declaredOutputs.clear();
        primaryKey = null;
        expectedPrimary = 0;
    }

    public P getPattern() {
        return pattern;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public Map<K, Long> getDeclaredOutputs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(declaredOutputs));
    }

    public K getPrimaryKey() {
        return primaryKey;
    }

    public long getExpectedPrimary() {
        return expectedPrimary;
    }

    private boolean canAdd(Map<K, Long> outputs, K candidatePrimaryKey, long candidatePrimaryAmount) {
        if (outputs.isEmpty() || candidatePrimaryKey == null || candidatePrimaryAmount <= 0) {
            return false;
        }
        if (expectedPrimary > Long.MAX_VALUE - candidatePrimaryAmount) {
            return false;
        }
        for (Map.Entry<K, Long> entry : outputs.entrySet()) {
            long amount = entry.getValue();
            if (entry.getKey() == null || amount <= 0) {
                return false;
            }
        }
        return true;
    }

}
