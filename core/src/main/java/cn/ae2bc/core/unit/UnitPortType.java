package cn.ae2bc.core.unit;

/** Platform-neutral identity and broad behavior categories for unit ports. */
public enum UnitPortType {
    DROP("drop", true, false, false, false),
    COLLECT("collect", false, true, true, false),
    PLACE("place", true, false, false, false),
    BREAK("break", false, true, true, false),
    TRANSFER("transfer", true, false, false, false),
    RETURN("return", false, true, false, false),
    EXTRACT("extract", false, true, true, false),
    REDSTONE("redstone", false, false, true, false),
    ENERGY("energy", false, false, false, true);

    private final String id;
    private final boolean acceptsTaskInput;
    private final boolean returnsTaskOutput;
    private final boolean needsTaskTick;
    private final boolean acceptsExternalEnergy;

    UnitPortType(String id, boolean acceptsTaskInput, boolean returnsTaskOutput,
            boolean needsTaskTick, boolean acceptsExternalEnergy) {
        this.id = id;
        this.acceptsTaskInput = acceptsTaskInput;
        this.returnsTaskOutput = returnsTaskOutput;
        this.needsTaskTick = needsTaskTick;
        this.acceptsExternalEnergy = acceptsExternalEnergy;
    }

    public String getId() { return id; }
    public boolean acceptsTaskInput() { return acceptsTaskInput; }
    public boolean returnsTaskOutput() { return returnsTaskOutput; }
    public boolean needsTaskTick() { return needsTaskTick; }
    public boolean acceptsExternalEnergy() { return acceptsExternalEnergy; }

    public static UnitPortType fromId(String id) {
        if (id != null) {
            for (UnitPortType type : values()) {
                if (type.id.equals(id)) return type;
            }
        }
        throw new IllegalArgumentException("Unknown unit port type: " + id);
    }

    public static UnitPortType forOutputFormId(int formId) {
        switch (formId) {
            case 1: return DROP;
            case 2: return PLACE;
            default: return TRANSFER;
        }
    }
}
