package cn.ae2bc.logic;

import cn.ae2bc.core.extraction.ExtractionBackoffPolicy;
import cn.ae2bc.core.extraction.ExtractionOutcome;

/** Computes the next extraction delay while bounding empty-machine polling. */
final class ProductExtractionBackoff {
    static final int MAX_IDLE_DELAY = ExtractionBackoffPolicy.MAX_IDLE_DELAY;
    private final ExtractionBackoffPolicy delegate = new ExtractionBackoffPolicy();

    int nextDelay(ProductExtractionTickState state, int configuredInterval) {
        return delegate.nextDelay(ExtractionOutcome.valueOf(state.name()), configuredInterval);
    }

    void reset() {
        delegate.reset();
    }
}
