package cn.ae2bc.logic;

import java.util.Objects;

final class ConfigurationSync {
    private ConfigurationSync() {
    }

    static <T> boolean shouldApply(T current, long currentRevision, T incoming, long incomingRevision) {
        return incoming != null
                && (currentRevision != incomingRevision || !Objects.equals(current, incoming));
    }
}
