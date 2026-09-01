package cn.ae2bc.pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputDirectionCodecTest {
    @Test
    void roundTripsAcrossEveryWordBoundary() {
        int[] slots = {0, 20, 21, 41, 42, 62, 63, 80};
        long[] packed = new long[InputDirectionCodec.WORD_COUNT];

        for (int i = 0; i < slots.length; i++) {
            packed = InputDirectionCodec.withCode(packed, slots[i], i % 6 + 1);
        }

        for (int i = 0; i < slots.length; i++) {
            assertEquals(i % 6 + 1, InputDirectionCodec.getCode(packed, slots[i]));
            assertEquals(i % 6, InputDirectionCodec.getDirectionOrdinal(packed, slots[i]));
        }
    }

    @Test
    void missingInvalidAndTruncatedDataReadsAsAutomatic() {
        assertEquals(-1, InputDirectionCodec.getDirectionOrdinal(null, 0));
        assertEquals(-1, InputDirectionCodec.getDirectionOrdinal(new long[0], 80));

        long[] malformed = {7};
        assertEquals(7, InputDirectionCodec.getCode(malformed, 0));
        assertEquals(-1, InputDirectionCodec.getDirectionOrdinal(malformed, 0));
    }

    @Test
    void invalidWritesBecomeAutomaticAndBoundsAreIgnored() {
        long[] packed = InputDirectionCodec.withCode(new long[InputDirectionCodec.WORD_COUNT], 0, 3);
        packed = InputDirectionCodec.withCode(packed, 0, 99);
        assertEquals(0, InputDirectionCodec.getCode(packed, 0));

        assertSame(packed, InputDirectionCodec.withCode(packed, -1, 1));
        assertSame(packed, InputDirectionCodec.withCode(packed, 81, 1));
        assertEquals(0, InputDirectionCodec.getCode(packed, -1));
        assertEquals(0, InputDirectionCodec.getCode(packed, 81));
    }

    @Test
    void changingOneSlotPreservesTheOthers() {
        long[] packed = InputDirectionCodec.withCode(null, 0, 6);
        packed = InputDirectionCodec.withCode(packed, 80, 1);
        packed = InputDirectionCodec.withCode(packed, 0, 0);

        assertEquals(-1, InputDirectionCodec.getDirectionOrdinal(packed, 0));
        assertEquals(0, InputDirectionCodec.getDirectionOrdinal(packed, 80));
    }

}
