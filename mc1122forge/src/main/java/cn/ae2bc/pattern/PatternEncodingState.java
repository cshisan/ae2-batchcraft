package cn.ae2bc.pattern;

import appeng.container.implementations.ContainerPatternTerm;
import cn.ae2bc.pattern.MaterialOutputConfigCodec;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Thread-local hand-off used by the rv6 pattern terminal adapter. */
public final class PatternEncodingState {
    private static final ThreadLocal<long[]> PENDING = new ThreadLocal<long[]>();

    private PatternEncodingState() { }

    public static void set(long[] packed) {
        PENDING.set(MaterialOutputConfigCodec.normalize(packed));
    }

    public static void prepare(Object container) {
        PENDING.remove();
        if (!(container instanceof ContainerPatternTerm)
                || ((ContainerPatternTerm) container).isCraftingMode()) {
            return;
        }
        MaterialOutputConfigData config = PatternEncodingTermMenuState.get(container);
        if (!config.isEmpty()) {
            set(config.toPacked());
        }
    }

    public static void clear() {
        PENDING.remove();
    }

    public static void apply(ItemStack stack) {
        long[] packed = PENDING.get();
        PENDING.remove();
        if (packed == null || packed.length == 0) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        // NBTTagLongArray/setLongArray was introduced after 1.12.2. Keep the
        // canonical long words but store each word as two 32-bit values.
        long[] normalized = MaterialOutputConfigCodec.normalize(packed);
        int[] packedWords = new int[normalized.length * 2];
        for (int i = 0; i < normalized.length; i++) {
            packedWords[i * 2] = (int) (normalized[i] >>> 32);
            packedWords[i * 2 + 1] = (int) normalized[i];
        }
        tag.setIntArray("MaterialOutputConfig", packedWords);
    }

    /** Reads the int-pair representation without exposing NBT to core. */
    public static long[] read(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null
                || !stack.getTagCompound().hasKey("MaterialOutputConfig", 11)) {
            return new long[MaterialOutputConfigCodec.WORD_COUNT];
        }
        int[] words = stack.getTagCompound().getIntArray("MaterialOutputConfig");
        long[] packed = new long[MaterialOutputConfigCodec.WORD_COUNT];
        for (int i = 0; i < packed.length && i * 2 + 1 < words.length; i++) {
            packed[i] = ((long) words[i * 2] << 32) | (words[i * 2 + 1] & 0xFFFFFFFFL);
        }
        return MaterialOutputConfigCodec.normalize(packed);
    }
}
