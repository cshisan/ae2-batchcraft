package cn.ae2bc.logic;

/** Describes the outcome of one product-extraction scheduling pass. */
public enum ProductExtractionTickState {
    DISABLED,
    WAITING,
    PROGRESSED,
    NO_PROGRESS
}
