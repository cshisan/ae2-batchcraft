package cn.ae2bc.pattern;

/** Pure bit-packing used by {@link InputDirectionData}. */
public final class InputDirectionCodec {
    public static final int MAX_SLOTS = 81;
    public static final int DIRECTION_COUNT = 6;
    static final int BITS_PER_SLOT = 3;
    public static final int SLOTS_PER_WORD = Long.SIZE / BITS_PER_SLOT;
    public static final int WORD_COUNT = (MAX_SLOTS + SLOTS_PER_WORD - 1) / SLOTS_PER_WORD;
    private static final long CODE_MASK = 0b111L;

    private InputDirectionCodec() {
    }

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < MAX_SLOTS;
    }

    public static int getCode(long[] packed, int slot) {
        if (packed == null || !isValidSlot(slot)) {
            return 0;
        }
        int word = slot / SLOTS_PER_WORD;
        if (word >= packed.length) {
            return 0;
        }
        int shift = slot % SLOTS_PER_WORD * BITS_PER_SLOT;
        return (int) ((packed[word] >>> shift) & CODE_MASK);
    }

    public static int getDirectionOrdinal(long[] packed, int slot) {
        int code = getCode(packed, slot);
        return code >= 1 && code <= DIRECTION_COUNT ? code - 1 : -1;
    }

    public static long[] withCode(long[] packed, int slot, int code) {
        if (!isValidSlot(slot)) {
            return packed;
        }
        int safeCode = sanitizeCode(code);
        long[] copy = new long[WORD_COUNT];
        if (packed != null) {
            System.arraycopy(packed, 0, copy, 0, Math.min(packed.length, copy.length));
        }
        int word = slot / SLOTS_PER_WORD;
        int shift = slot % SLOTS_PER_WORD * BITS_PER_SLOT;
        copy[word] = copy[word] & ~(CODE_MASK << shift) | (long) safeCode << shift;
        return copy;
    }

    static int sanitizeCode(int code) {
        return code >= 0 && code <= DIRECTION_COUNT ? code : 0;
    }
}
