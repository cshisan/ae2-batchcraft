package cn.ae2bc.logic;

public enum RedstoneOutputMode {
    PERIODIC_PULSE(0, "periodic_pulse"),
    SINGLE_TRIGGER(1, "single_trigger"),
    CONTINUOUS(2, "continuous");

    private final int id;
    private final String serializedName;

    RedstoneOutputMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public RedstoneOutputMode next() {
        switch (this) {
            case SINGLE_TRIGGER:
                return PERIODIC_PULSE;
            case PERIODIC_PULSE:
                return CONTINUOUS;
            case CONTINUOUS:
            default:
                return SINGLE_TRIGGER;
        }
    }

    public static RedstoneOutputMode fromId(int id) {
        switch (id) {
            case 0:
                return PERIODIC_PULSE;
            case 2:
                return CONTINUOUS;
            default:
                return SINGLE_TRIGGER;
        }
    }
}
