package org.ae2bc.core.unit;

public enum RedstoneMode {
    ONCE(0),
    PULSE(1),
    CONTINUOUS(2),
    ;

    public final int id;

    RedstoneMode(int id) {
        this.id = id;
    }

    public static RedstoneMode null2defaultOf(int id) {
        for (RedstoneMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return ONCE;
    }
}
