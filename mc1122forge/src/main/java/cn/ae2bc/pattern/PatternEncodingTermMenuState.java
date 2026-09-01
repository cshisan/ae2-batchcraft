package cn.ae2bc.pattern;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Per-open-container material-output state; weak keys prevent abandoned terminals from being retained. */
public final class PatternEncodingTermMenuState {
    private static final Map<Object, MaterialOutputConfigData> CONFIGS = Collections.synchronizedMap(
            new WeakHashMap<Object, MaterialOutputConfigData>());

    private PatternEncodingTermMenuState() { }

    public static void set(Object container, long[] packed) {
        if (container == null) return;
        CONFIGS.put(container, MaterialOutputConfigData.fromPacked(packed));
    }

    public static MaterialOutputConfigData get(Object container) {
        MaterialOutputConfigData config = CONFIGS.get(container);
        return config == null ? MaterialOutputConfigData.EMPTY : config;
    }
}
