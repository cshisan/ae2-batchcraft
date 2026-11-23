package org.ae2bc.core.pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MaterialOutputAttrHelperTest {
    @Test
    void everySlotAndModeRoundTripsWithoutChangingNeighbors() {
        long[] packed = null;
        MaterialOutputMode[] modes = MaterialOutputMode.values();
        int[] expected = new int[DirectionHelper.MAX_SLOTS];
        for (int slot = 0; slot < expected.length; slot++) {
            expected[slot] = modes[slot % modes.length].id();
            packed = MaterialOutputAttrHelper.withModeId(packed, slot, expected[slot]);
        }
        for (int slot = 0; slot < expected.length; slot++) {
            for (MaterialOutputMode mode : modes) {
                long[] changed = MaterialOutputAttrHelper.withModeId(packed, slot, mode.id());
                for (int other = 0; other < expected.length; other++) {
                    Assertions.assertEquals(other == slot ? mode.id() : expected[other],
                            MaterialOutputAttrHelper.getModeId(changed, other),
                            "changed=" + slot + " checked=" + other + " mode=" + mode);
                }
                Assertions.assertEquals(expected[slot], MaterialOutputAttrHelper.getModeId(packed, slot));
            }
        }
    }

    @Test
    void completeConfigurationReadsFormsAfterDirectionWords() {
        MaterialOutputMode[] modes = MaterialOutputMode.values();
        long[] packed = new long[MaterialOutputAttrHelper.WORD_COUNT];
        long[] modesPacked = null;
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            modesPacked = MaterialOutputAttrHelper.withModeId(
                    modesPacked, slot, modes[slot % modes.length].id());
        }
        System.arraycopy(
                modesPacked, 0, packed, MaterialOutputAttrHelper.DIRECTION_WORDS, modesPacked.length);
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            Assertions.assertEquals(modes[slot % modes.length].id(), MaterialOutputAttrHelper.getModeId(
                    packed, slot, MaterialOutputAttrHelper.DIRECTION_WORDS));
        }
        Assertions.assertTrue(MaterialOutputAttrHelper.containsExplicitMode(
                packed, MaterialOutputAttrHelper.DIRECTION_WORDS));
    }

    @Test
    void invalidSlotsAndMissingWordsRemainDefault() {
        Assertions.assertEquals(0, MaterialOutputAttrHelper.getModeId(null, 0));
        Assertions.assertEquals(0, MaterialOutputAttrHelper.getModeId(new long[] {-1L}, -1));
        Assertions.assertEquals(0, MaterialOutputAttrHelper.getModeId(new long[] {-1L}, 81));
        Assertions.assertEquals(0, MaterialOutputAttrHelper.getModeId(new long[] {-1L}, 32));
        Assertions.assertEquals(0, MaterialOutputAttrHelper.getModeId(new long[] {-1L}, 0, Integer.MAX_VALUE));
    }

    @Test
    void bothFormBitsRepresentFakeCraftingConverterRatherThanAnInvalidMode() {
        Assertions.assertEquals(MaterialOutputMode.FAKE_CRAFTING_CONVERTER.id(),
                MaterialOutputAttrHelper.getModeId(new long[] {-1L}, 0));
        Assertions.assertEquals(MaterialOutputMode.FAKE_CRAFTING_CONVERTER.id(),
                MaterialOutputAttrHelper.getModeId(new long[] {-1L}, 31));
    }
}
