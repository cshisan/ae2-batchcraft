package cn.ae2bc.core.unit;

/** Controls whether output ports may serve more than one encoded pattern material slot. */
public enum OutputSlotSharingMode {
    ALL(0, "all"),
    DISABLED(1, "disabled"),
    FOLLOW_PORT(2, "follow_port");

    private final int id;
    private final String serializedName;

    OutputSlotSharingMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public OutputSlotSharingMode next() {
        OutputSlotSharingMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static OutputSlotSharingMode fromId(int id) {
        for (OutputSlotSharingMode value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return DISABLED;
    }
}
