package cn.ae2bc.core.dispatch;

/** Determines how a task destination is selected. */
public enum TaskAllocationMode {
    ROUND_ROBIN(0),
    RANDOM(1),
    PRIORITY(2);

    private final int id;

    TaskAllocationMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public TaskAllocationMode next() {
        TaskAllocationMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TaskAllocationMode fromId(int id) {
        for (TaskAllocationMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return ROUND_ROBIN;
    }
}
