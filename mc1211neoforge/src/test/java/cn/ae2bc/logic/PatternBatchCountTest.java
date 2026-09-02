package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternBatchCountTest {
    @Test
    void calculatesMaximumFromAllPositiveAmounts() {
        assertEquals(20, PatternBatchCount.maximum(new long[]{100, 200, 40}));
        assertEquals(1, PatternBatchCount.maximum(new long[]{0, -1}));
    }

    @Test
    void correctsInvalidAndExcessiveValues() {
        assertValidation(1, PatternBatchCount.Result.INVALID,
                PatternBatchCount.validate(0, 100));
        assertValidation(100, PatternBatchCount.Result.ABOVE_MAXIMUM,
                PatternBatchCount.validate(200, 100));
        assertValidation(1, PatternBatchCount.Result.NOT_DIVISIBLE,
                PatternBatchCount.validate(30, 100));
        assertValidation(20, PatternBatchCount.Result.VALID,
                PatternBatchCount.validate(20, 100));
    }

    private static void assertValidation(long value, PatternBatchCount.Result result,
                                         PatternBatchCount.Validation validation) {
        assertEquals(value, validation.getValue());
        assertEquals(result, validation.getResult());
    }
}
