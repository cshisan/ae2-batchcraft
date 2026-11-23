package org.ae2bc.core.pattern;

import java.util.Arrays;

public class MaterialOutputAttrHelper {
    public static final int DIRECTION_WORDS = DirectionHelper.WORD_COUNT;
    private static final int MODE_BITS = 2;
    private static final long MODE_MASK = (1L << MODE_BITS) - 1;
    private static final int MODES_PER_WORD = Long.SIZE / MODE_BITS;
    public static final int MODE_WORDS = (DirectionHelper.MAX_SLOTS + MODES_PER_WORD - 1) / MODES_PER_WORD;
    public static final int WORD_COUNT = DIRECTION_WORDS + MODE_WORDS;
    private static final int MAX_MODE_ID = 3;

    private MaterialOutputAttrHelper() {
    }

    public static long[] normalize(long[] packed) {
        return packed == null ? new long[WORD_COUNT] : Arrays.copyOf(packed, WORD_COUNT);
    }

    public static long[] directionWords(long[] packed) {
        return Arrays.copyOf(MaterialOutputAttrHelper.normalize(packed), DIRECTION_WORDS);
    }

    public static long[] modeWords(long[] packed) {
        return Arrays.copyOfRange(MaterialOutputAttrHelper.normalize(packed), DIRECTION_WORDS, WORD_COUNT);
    }

    public static int getModeId(long[] packed, int slot) {
        return MaterialOutputAttrHelper.getModeId(packed, slot, 0);
    }

    /* 按输出方式的两位格式读取，允许直接读取完整配置中的方式区域。 */
    public static int getModeId(long[] packed, int slot, int wordOffset) {
        if (packed == null || DirectionHelper.invalidSlot(slot) || wordOffset < 0) {
            return 0;
        }
        int word = slot / MODES_PER_WORD;
        if (wordOffset >= packed.length || word >= packed.length - wordOffset) {
            return 0;
        }
        int shift = slot % MODES_PER_WORD * MODE_BITS;
        return (int) ((packed[wordOffset + word] >>> shift) & MODE_MASK);
    }

    public static long[] withModeId(long[] packed, int slot, int modeId) {
        if (DirectionHelper.invalidSlot(slot)) {
            return packed;
        }
        long[] copy = packed == null ? new long[MODE_WORDS] : Arrays.copyOf(packed, MODE_WORDS);
        int word = slot / MODES_PER_WORD;
        int shift = slot % MODES_PER_WORD * MODE_BITS;
        int safeModeId = modeId >= 0 && modeId <= MAX_MODE_ID ? modeId : 0;
        copy[word] = copy[word] & ~(MODE_MASK << shift) | (long) safeModeId << shift;
        return copy;
    }

    public static boolean containsExplicitMode(long[] modes, int wordOffset) {
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            if (MaterialOutputAttrHelper.getModeId(modes, slot, wordOffset) != 0) {
                return true;
            }
        }
        return false;
    }
}
