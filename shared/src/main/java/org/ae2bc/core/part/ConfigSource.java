package org.ae2bc.core.part;

import java.util.LinkedList;
import java.util.List;

public class ConfigSource<Config> {
    private final List<ConfigLocal<Config>> listeners = new LinkedList<>();

    public void register(ConfigLocal<Config> listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(ConfigLocal<Config> listener) {
        listeners.remove(listener);
    }

    public void firePush(Config config) {
        listeners.forEach(listener -> listener.apply(config));
    }
}
