package cn.ae2bc.logic;

public enum PatternDispatchMode {
    FULL_DISPATCH,
    BATCH_DISTRIBUTION;

    public static PatternDispatchMode fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : FULL_DISPATCH;
    }
}
