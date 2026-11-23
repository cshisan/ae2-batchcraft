package org.ae2bc.core.dispatch;

/* 限制单次调度的访问次数，子预算同时计入所属总预算。 */
public final class WorkBudget {
    private final WorkBudget parent;
    private int remaining;

    public WorkBudget(int limit) {
        this(null, limit);
    }

    private WorkBudget(WorkBudget parent, int limit) {
        this.parent = parent;
        this.remaining = Math.max(0, limit);
    }

    public WorkBudget limitedTo(int limit) {
        return new WorkBudget(this, limit);
    }

    public int remaining() {
        return this.parent == null ? this.remaining : Math.min(this.remaining, this.parent.remaining());
    }

    /* 预检占用一次额度，同时为对应的实际访问预留执行机会。 */
    public boolean tryUse() {
        if (this.remaining <= 0 || (this.parent != null && !this.parent.tryUse())) {
            return false;
        }
        this.remaining--;
        return true;
    }
}
