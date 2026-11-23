package org.ae2bc.core.dispatch;

/** 确定如何选择任务目标端点。 */
public enum TaskAllocationMode {
    ROUND_ROBIN(0),
    RANDOM(1),
    PRIORITY(2);

    public final int id;
    public final String name;

    TaskAllocationMode(int id) {
        this.id = id;
        this.name = this.name().toLowerCase();
    }

    public TaskAllocationMode next() {
        TaskAllocationMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TaskAllocationMode null2defaultOf(int id) {
        for (TaskAllocationMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return ROUND_ROBIN;
    }
}
