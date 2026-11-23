package org.ae2bc.core.unit;

/**
 * 控制任务可通过输出端点返回哪些产物。
 */
public enum ReturnMode {
    UNBLOCKED(0),
    STRICT_FULL(1),
    STRICT_IGNORE_NBT(2),
    STRICT_CHECK_RID(3),
    ;

    public final int id;
    public final String name;

    ReturnMode(int id) {
        this.id = id;
        this.name = this.name().toLowerCase();
    }

    public static ReturnMode null2defaultOf(int id) {
        for (ReturnMode value : ReturnMode.values()) {
            if (value.id == id) {
                return value;
            }
        }
        return UNBLOCKED;
    }

    public ReturnMode next() {
        switch (this) {
            case STRICT_FULL:
                return STRICT_IGNORE_NBT;
            case STRICT_IGNORE_NBT:
                return STRICT_CHECK_RID;
            default:
                return STRICT_FULL;
        }
    }
}
