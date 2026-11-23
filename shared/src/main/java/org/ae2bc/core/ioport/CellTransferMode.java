package org.ae2bc.core.ioport;

public enum CellTransferMode {
    SLOTS("slots"),
    EXTERNAL("external"),
    PREFER_SLOTS("prefer_slots"),
    PREFER_EXTERNAL("prefer_external");

    private static final CellTransferMode[] MODES = CellTransferMode.values();
    private final String id;

    CellTransferMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean allowsExternal() {
        return this != SLOTS;
    }

    public CellTransferMode next() {
        return MODES[(this.ordinal() + 1) % MODES.length];
    }

    public static CellTransferMode fromId(String id) {
        for (CellTransferMode mode : MODES) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return SLOTS;
    }
}
