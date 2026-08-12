package cn.ae2bc.pattern;

import cn.ae2bc.pattern.MaterialOutputConfigCodec;
import net.minecraft.item.ItemStack;

/** Per-thread hand-off from the pattern terminal bridge to AE2's final item write. */
public final class PatternEncodingState {
    private static final ThreadLocal<long[]> PENDING = new ThreadLocal<long[]>();

    private PatternEncodingState() { }

    public static void set(long[] packed) {
        PENDING.set(MaterialOutputConfigCodec.normalize(packed));
    }

    public static void clear() {
        PENDING.remove();
    }

    public static long[] consume() {
        long[] result = PENDING.get();
        PENDING.remove();
        return result;
    }

    public static long[] read(ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getTag().contains("MaterialOutputConfig")) {
            return new long[MaterialOutputConfigCodec.WORD_COUNT];
        }
        return MaterialOutputConfigCodec.normalize(stack.getTag().getLongArray("MaterialOutputConfig"));
    }
}
