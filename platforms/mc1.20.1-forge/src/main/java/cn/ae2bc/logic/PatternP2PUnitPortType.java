package cn.ae2bc.logic;

import cn.ae2bc.pattern.MaterialOutputForm;

public enum PatternP2PUnitPortType {
    DROP("drop"),
    COLLECT("collect"),
    PLACE("place"),
    BREAK("break"),
    TRANSFER("transfer"),
    RETURN("return"),
    EXTRACT("extract"),
    REDSTONE("redstone"),
    ENERGY("energy");

    private final String serializedName;

    PatternP2PUnitPortType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public boolean acceptsExternalEnergy() {
        return this == ENERGY;
    }

    public static PatternP2PUnitPortType forOutputForm(MaterialOutputForm form) {
        return switch (form) {
            case NORMAL -> TRANSFER;
            case DROP -> DROP;
            case PLACE -> PLACE;
        };
    }
}
