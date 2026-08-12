package cn.ae2bc.logic;

public enum EnergyDistributionMode {
    EVEN(0, "even"),
    ROUND_ROBIN(1, "round_robin");

    private final int id;
    private final String serializedName;

    EnergyDistributionMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public EnergyDistributionMode next() {
        return this == EVEN ? ROUND_ROBIN : EVEN;
    }

    public static EnergyDistributionMode fromId(int id) {
        return id == ROUND_ROBIN.id ? ROUND_ROBIN : EVEN;
    }
}
