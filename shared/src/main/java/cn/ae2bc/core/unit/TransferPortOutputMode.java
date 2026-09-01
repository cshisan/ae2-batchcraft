package cn.ae2bc.core.unit;

/** How a unit transfer port is allowed to receive materials during one task. */
public enum TransferPortOutputMode {
    NORMAL(0, "normal"),
    SINGLE_ITEM(1, "single_item"),
    SAME_TYPE(2, "same_type");

    private final int id;
    private final String serializedName;

    TransferPortOutputMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public static TransferPortOutputMode fromId(int id) {
        for (TransferPortOutputMode value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return NORMAL;
    }
}
