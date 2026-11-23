package org.ae2bc.core.unit;

public enum OutputPortMode {
    NORMAL(0),
    SLOT_EXCLUSIVE(1),
    SINGLE(2),
    SAME_TYPE(3);

    public final int id;

    OutputPortMode(int id) {
        this.id = id;
    }

    public static OutputPortMode null2defaultOf(int id) {
        switch (id) {
            case 1: return SLOT_EXCLUSIVE;
            case 2: return SINGLE;
            case 3: return SAME_TYPE;
            default: return NORMAL;
        }
    }
}
