package cn.ae2bc.logic;

import cn.ae2bc.core.extraction.ProductExtractionLimits;

/** Limits successful product-extraction rounds for one grid and each endpoint during a server tick. */
public final class ProductExtractionBudget {
    private final int maxRoundsPerEndpoint;
    private int remainingGridRounds;
    private int remainingEndpointRounds;

    public ProductExtractionBudget() {
        this(ProductExtractionLimits.MAX_TRANSFER_ROUNDS_PER_ENDPOINT_TICK,
                ProductExtractionLimits.MAX_TRANSFER_ROUNDS_PER_GRID_TICK);
    }

    ProductExtractionBudget(int maxRoundsPerEndpoint, int maxRoundsPerGrid) {
        if (maxRoundsPerEndpoint <= 0 || maxRoundsPerGrid <= 0) {
            throw new IllegalArgumentException("Extraction round limits must be positive");
        }
        this.maxRoundsPerEndpoint = maxRoundsPerEndpoint;
        this.remainingGridRounds = maxRoundsPerGrid;
    }

    public void beginEndpoint() {
        remainingEndpointRounds = Math.min(maxRoundsPerEndpoint, remainingGridRounds);
    }

    public boolean hasRemainingRound() {
        return remainingEndpointRounds > 0 && remainingGridRounds > 0;
    }

    public boolean consumeSuccessfulRound() {
        if (!hasRemainingRound()) {
            return false;
        }
        remainingEndpointRounds--;
        remainingGridRounds--;
        return true;
    }

    public boolean isEndpointExhausted() {
        return remainingEndpointRounds <= 0;
    }

    public boolean isGridExhausted() {
        return remainingGridRounds <= 0;
    }

    int remainingEndpointRounds() {
        return remainingEndpointRounds;
    }

    int remainingGridRounds() {
        return remainingGridRounds;
    }
}
