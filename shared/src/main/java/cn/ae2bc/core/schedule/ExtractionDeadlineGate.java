package cn.ae2bc.core.schedule;

/**
 * Small version-neutral gate for per-endpoint extraction. It uses the server's
 * absolute tick rather than the AE2 ticker's variable callback interval, so a
 * delayed callback cannot cause an endpoint to run more often than configured.
 */
public final class ExtractionDeadlineGate {
    private long deadline = Long.MIN_VALUE;

    public boolean isDue(long now, int interval) {
        if (deadline == Long.MIN_VALUE || now >= deadline) {
            long delay = Math.max(1L, interval);
            deadline = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
            return true;
        }
        return false;
    }

    public void wake() {
        deadline = Long.MIN_VALUE;
    }

    public void clear() {
        deadline = Long.MIN_VALUE;
    }

    public long deadline() {
        return deadline;
    }
}
