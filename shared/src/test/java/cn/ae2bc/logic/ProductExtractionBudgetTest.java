package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductExtractionBudgetTest {
    @Test
    void limitsEachEndpointAndSharesTheGridBudget() {
        ProductExtractionBudget budget = new ProductExtractionBudget(2, 3);

        budget.beginEndpoint();
        assertTrue(budget.consumeSuccessfulRound());
        assertTrue(budget.consumeSuccessfulRound());
        assertFalse(budget.hasRemainingRound());
        assertEquals(1, budget.remainingGridRounds());

        budget.beginEndpoint();
        assertTrue(budget.consumeSuccessfulRound());
        assertTrue(budget.isGridExhausted());
        assertFalse(budget.consumeSuccessfulRound());
    }

    @Test
    void startingAnEndpointDoesNotRestoreTheGridBudget() {
        ProductExtractionBudget budget = new ProductExtractionBudget(3, 2);
        budget.beginEndpoint();
        assertTrue(budget.consumeSuccessfulRound());

        budget.beginEndpoint();
        assertEquals(1, budget.remainingEndpointRounds());
        assertEquals(1, budget.remainingGridRounds());
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ProductExtractionBudget(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProductExtractionBudget(1, 0));
    }
}
