package cn.ae2bc.core.extraction;

/** Result of one platform extraction pass. */
public enum ExtractionOutcome {
    DISABLED,
    WAITING,
    BUDGET_EXHAUSTED,
    PROGRESSED,
    NO_PROGRESS
}
