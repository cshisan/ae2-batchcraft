package org.ae2bc.core.dispatch;

public enum TaskSplitMode {
    FULL(0),
    BATCH(1);

    public final int id;
    public final String name;

    TaskSplitMode(int id) {
        this.id = id;
        this.name = this.name().toLowerCase();
    }

    public TaskSplitMode next() {
        TaskSplitMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TaskSplitMode null2defaultOf(int id) {
        for (TaskSplitMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return FULL;
    }
}
