package cn.ae2bc.logic;

/**
 * Controls which products a task may return through its output endpoint.
 */
public enum ReturnMode {
    STRICT(1, "strict"),
    UNBLOCKED(0, "unblocked");

    private final int id;
    private final String serializedName;

    ReturnMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public static ReturnMode fromId(int id) {
        switch (id) {
            case 1:
                return STRICT;
            case 0:
            default:
                return UNBLOCKED;
        }
    }
}
