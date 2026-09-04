package cn.ae2bc.logic;

import java.util.Objects;

/** Platform-independent state machine for a locally stored value with optional upstream broadcasts. */
public final class ConfigurationSync {
    private ConfigurationSync() {
    }

    public static <T> boolean shouldApply(T current, long currentRevision, T incoming, long incomingRevision) {
        return incoming != null
                && (currentRevision != incomingRevision || !Objects.equals(current, incoming));
    }

    /**
     * Stores the value that is actually used by an endpoint or manager. Broadcasts are
     * accepted only while synchronization is enabled; disabling synchronization never
     * changes the current local value.
     */
    public static final class State<T> {
        private T value;
        private long lastAppliedRevision;
        private boolean synchronizationEnabled;

        public State(T initialValue, boolean synchronizationEnabled, long lastAppliedRevision) {
            this.value = Objects.requireNonNull(initialValue, "initialValue");
            this.synchronizationEnabled = synchronizationEnabled;
            this.lastAppliedRevision = lastAppliedRevision;
        }

        public T value() {
            return value;
        }

        public long lastAppliedRevision() {
            return lastAppliedRevision;
        }

        public boolean isSynchronizationEnabled() {
            return synchronizationEnabled;
        }

        public boolean setSynchronizationEnabled(boolean enabled) {
            if (synchronizationEnabled == enabled) {
                return false;
            }
            synchronizationEnabled = enabled;
            return true;
        }

        public boolean setLocalValue(T value) {
            Objects.requireNonNull(value, "value");
            if (Objects.equals(this.value, value)) {
                return false;
            }
            this.value = value;
            return true;
        }

        public boolean applyBroadcast(T incoming, long incomingRevision) {
            if (!synchronizationEnabled || !shouldApply(value, lastAppliedRevision, incoming, incomingRevision)) {
                return false;
            }
            value = incoming;
            lastAppliedRevision = incomingRevision;
            return true;
        }

        public void restore(T value, long lastAppliedRevision, boolean synchronizationEnabled) {
            this.value = Objects.requireNonNull(value, "value");
            this.lastAppliedRevision = lastAppliedRevision;
            this.synchronizationEnabled = synchronizationEnabled;
        }
    }
}
