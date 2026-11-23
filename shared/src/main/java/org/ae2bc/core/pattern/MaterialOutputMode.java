package org.ae2bc.core.pattern;

import org.ae2bc.core.unit.UnitPortType;

public enum MaterialOutputMode {
    NORMAL(0),
    DROP(1),
    PLACE(2),
    FAKE_CRAFTING_CONVERTER(3);

    private final int id;

    MaterialOutputMode(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }

    public static MaterialOutputMode null2defaultOf(int id) {
        for (MaterialOutputMode value : MaterialOutputMode.values()) {
            if (value.id == id) {
                return value;
            }
        }
        return NORMAL;
    }

    public static MaterialOutputMode fromId(int id) {
        return null2defaultOf(id);
    }

    public UnitPortType matchPort() {
        if (this == DROP) {
            return UnitPortType.DROP;
        }
        if (this == PLACE) {
            return UnitPortType.PLACE;
        }
        if (this == FAKE_CRAFTING_CONVERTER) {
            return UnitPortType.TRANSFER;
        }
        return UnitPortType.TRANSFER;
    }
}
