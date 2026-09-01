package cn.ae2bc.pattern;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialOutputConfigCodecTest {
    @Test
    void canonicalFormatContainsFourDirectionAndThreeFormWords() {
        assertEquals(4, MaterialOutputConfigCodec.DIRECTION_WORDS);
        assertEquals(3, MaterialOutputConfigCodec.FORM_WORDS);
        assertEquals(7, MaterialOutputConfigCodec.WORD_COUNT);
    }

    @Test
    void currentPackedFormatRoundTripsAllProtocolWords() {
        long[] packed = {
                0x0123_4567_89AB_CDEFL, 0x0246_8ACE_1357_9BDFL,
                0x1357_9BDF_0246_8ACEL, 0x0000_0000_0001_2345L,
                0x0123_4567_89AB_CDEFL, 0x0246_8ACE_1357_9BDFL,
                0x0000_0000_0001_2345L
        };

        assertArrayEquals(Arrays.copyOf(packed, 4), MaterialOutputConfigCodec.directionWords(packed));
        assertArrayEquals(Arrays.copyOfRange(packed, 4, 7), MaterialOutputConfigCodec.formWords(packed));
        assertArrayEquals(packed, MaterialOutputConfigCodec.normalize(packed));
    }

    @Test
    void formSlotsRoundTripAcrossEveryWordBoundary() {
        int[] slots = {0, 31, 32, 63, 64, 80};
        long[] forms = null;
        for (int i = 0; i < slots.length; i++) {
            forms = MaterialOutputConfigCodec.withOutputFormId(forms, slots[i], i % 2 + 1);
        }
        for (int i = 0; i < slots.length; i++) {
            assertEquals(i % 2 + 1, MaterialOutputConfigCodec.getOutputFormId(forms, slots[i]));
        }
        assertTrue(MaterialOutputConfigCodec.containsExplicitForm(forms));
        assertFalse(MaterialOutputConfigCodec.containsExplicitForm(new long[3]));
    }

    @Test
    void invalidWritesBecomeNormalAndBoundsAreIgnored() {
        long[] forms = MaterialOutputConfigCodec.withOutputFormId(null, 0, 2);
        forms = MaterialOutputConfigCodec.withOutputFormId(forms, 0, 99);
        assertEquals(0, MaterialOutputConfigCodec.getOutputFormId(forms, 0));
        assertSame(forms, MaterialOutputConfigCodec.withOutputFormId(forms, -1, 1));
        assertSame(forms, MaterialOutputConfigCodec.withOutputFormId(forms, 81, 1));
    }

    @Test
    void reservedPackedFormCodeReadsAsNormalAndDoesNotKeepConfigAlive() {
        long[] forms = {3, 0, 0};

        assertEquals(0, MaterialOutputConfigCodec.getOutputFormId(forms, 0));
        assertFalse(MaterialOutputConfigCodec.containsExplicitForm(forms));
    }
}
