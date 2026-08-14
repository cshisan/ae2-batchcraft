package cn.ae2bc.pattern;

import java.util.Arrays;

/** Pure bit-packing for the canonical material output configuration format. */
public final class MaterialOutputConfigCodec {
    public static final int DIRECTION_WORDS = InputDirectionCodec.WORD_COUNT;
    private static final int FORM_BITS = 2;
    private static final int FORM_SLOTS_PER_WORD = Long.SIZE / FORM_BITS;
    public static final int FORM_WORDS =
            (InputDirectionCodec.MAX_SLOTS + FORM_SLOTS_PER_WORD - 1) / FORM_SLOTS_PER_WORD;
    public static final int WORD_COUNT = DIRECTION_WORDS + FORM_WORDS;
    private static final long FORM_MASK = 0b11L;
    private static final int MAX_FORM_ID = 2;

    private MaterialOutputConfigCodec() {
    }

    public static long[] normalize(long[] packed) {
        return packed == null ? new long[WORD_COUNT] : Arrays.copyOf(packed, WORD_COUNT);
    }

    public static long[] directionWords(long[] packed) {
        return Arrays.copyOf(normalize(packed), DIRECTION_WORDS);
    }

    public static long[] formWords(long[] packed) {
        return Arrays.copyOfRange(normalize(packed), DIRECTION_WORDS, WORD_COUNT);
    }

    public static int getOutputFormId(long[] forms, int slot) {
        if (!InputDirectionCodec.isValidSlot(slot) || forms == null) {
            return 0;
        }
        int word = slot / FORM_SLOTS_PER_WORD;
        if (word >= forms.length) {
            return 0;
        }
        int shift = slot % FORM_SLOTS_PER_WORD * FORM_BITS;
        int formId = (int) ((forms[word] >>> shift) & FORM_MASK);
        return formId <= MAX_FORM_ID ? formId : 0;
    }

    public static long[] withOutputFormId(long[] forms, int slot, int formId) {
        if (!InputDirectionCodec.isValidSlot(slot)) {
            return forms;
        }
        long[] copy = forms == null ? new long[FORM_WORDS] : Arrays.copyOf(forms, FORM_WORDS);
        int word = slot / FORM_SLOTS_PER_WORD;
        int shift = slot % FORM_SLOTS_PER_WORD * FORM_BITS;
        int safeFormId = formId >= 0 && formId <= MAX_FORM_ID ? formId : 0;
        copy[word] = copy[word] & ~(FORM_MASK << shift) | (long) safeFormId << shift;
        return copy;
    }

    public static boolean containsExplicitForm(long[] forms) {
        for (int slot = 0; slot < InputDirectionCodec.MAX_SLOTS; slot++) {
            if (getOutputFormId(forms, slot) != 0) {
                return true;
            }
        }
        return false;
    }
}
