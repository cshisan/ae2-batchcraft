package cn.ae2bc.logic;

/** Version-neutral retry delays for failed dispatch planning and committed remainders. */
public final class DispatchBackoffPolicy {
    public static final int MAX_PLANNING_DELAY = 100;
    public static final int MAX_PENDING_DELAY = 20;

    private DispatchBackoffPolicy() {
    }

    public static int planningDelay(int consecutiveFailures) {
        return delay(consecutiveFailures, 5, MAX_PLANNING_DELAY);
    }

    public static int pendingDelay(int consecutiveFailures) {
        return delay(consecutiveFailures, 1, MAX_PENDING_DELAY);
    }

    private static int delay(int consecutiveFailures, int initialDelay, int maximumDelay) {
        if (consecutiveFailures <= 1) {
            return initialDelay;
        }
        long delay = (long) initialDelay << Math.min(30, consecutiveFailures - 1);
        return (int) Math.min(maximumDelay, delay);
    }
}
