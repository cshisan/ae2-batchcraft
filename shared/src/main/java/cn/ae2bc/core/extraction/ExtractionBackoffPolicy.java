package cn.ae2bc.core.extraction;

/** Version-neutral extraction deadline/backoff policy. */
public final class ExtractionBackoffPolicy {
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 2000;
    public static final int MAX_IDLE_DELAY = 20;
    private int idleDelay = MIN_INTERVAL;

    public int nextDelay(ExtractionOutcome outcome, int configuredInterval) {
        int interval = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, configuredInterval));
        switch (outcome) {
            case PROGRESSED:
                idleDelay = MIN_INTERVAL;
                return interval;
            case NO_PROGRESS:
                int result = Math.max(interval, idleDelay);
                idleDelay = Math.min(MAX_IDLE_DELAY, idleDelay * 2);
                return result;
            case WAITING:
            case BUDGET_EXHAUSTED:
                return MIN_INTERVAL;
            case DISABLED:
                return Integer.MAX_VALUE;
            default:
                throw new IllegalArgumentException("Unknown extraction outcome: " + outcome);
        }
    }

    public void reset() {
        idleDelay = MIN_INTERVAL;
    }
}
