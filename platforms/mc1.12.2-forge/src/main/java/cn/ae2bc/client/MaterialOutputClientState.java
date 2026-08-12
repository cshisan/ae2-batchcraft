package cn.ae2bc.client;

import cn.ae2bc.pattern.MaterialOutputConfigData;

/** Client-only cache used while switching between the terminal and its output-configuration page. */
public final class MaterialOutputClientState {
    private static int windowId = -1;
    private static MaterialOutputConfigData config = MaterialOutputConfigData.EMPTY;

    private MaterialOutputClientState() { }

    public static MaterialOutputConfigData get(int currentWindowId) {
        ensureWindow(currentWindowId);
        return config;
    }

    public static void set(int currentWindowId, MaterialOutputConfigData value) {
        ensureWindow(currentWindowId);
        config = value == null ? MaterialOutputConfigData.EMPTY : value;
    }

    private static void ensureWindow(int currentWindowId) {
        if (windowId != currentWindowId) {
            windowId = currentWindowId;
            config = MaterialOutputConfigData.EMPTY;
        }
    }
}
