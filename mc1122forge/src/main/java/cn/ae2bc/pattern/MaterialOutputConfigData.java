package cn.ae2bc.pattern;

import java.util.Arrays;
import net.minecraft.util.EnumFacing;

/** Per-slot processing-pattern configuration containing output direction and output form. */
public final class MaterialOutputConfigData {
    public static final MaterialOutputConfigData EMPTY = new MaterialOutputConfigData(
            new long[MaterialOutputConfigCodec.WORD_COUNT]);

    private final long[] packed;
    private final boolean empty;

    private MaterialOutputConfigData(long[] packed) {
        this.packed = MaterialOutputConfigCodec.normalize(packed);
        this.empty = isPackedEmpty(this.packed);
    }

    public static MaterialOutputConfigData fromPacked(long[] packed) {
        MaterialOutputConfigData value = new MaterialOutputConfigData(packed);
        return value.empty ? EMPTY : value;
    }

    public long[] toPacked() {
        return Arrays.copyOf(packed, packed.length);
    }

    public EnumFacing getDirection(int slot) {
        int ordinal = InputDirectionCodec.getDirectionOrdinal(
                MaterialOutputConfigCodec.directionWords(packed), slot);
        return ordinal < 0 || ordinal >= EnumFacing.values().length ? null : EnumFacing.values()[ordinal];
    }

    public MaterialOutputForm getOutputForm(int slot) {
        return MaterialOutputForm.fromId(MaterialOutputConfigCodec.getOutputFormId(
                MaterialOutputConfigCodec.formWords(packed), slot));
    }

    public MaterialOutputConfigData withDirection(int slot, EnumFacing direction) {
        long[] directions = MaterialOutputConfigCodec.directionWords(packed);
        directions = InputDirectionCodec.withCode(directions, slot,
                direction == null ? 0 : direction.ordinal() + 1);
        return fromParts(directions, MaterialOutputConfigCodec.formWords(packed));
    }

    public MaterialOutputConfigData withOutputForm(int slot, MaterialOutputForm form) {
        MaterialOutputForm safeForm = form == null ? MaterialOutputForm.NORMAL : form;
        long[] forms = MaterialOutputConfigCodec.withOutputFormId(
                MaterialOutputConfigCodec.formWords(packed), slot, safeForm.getId());
        return fromParts(MaterialOutputConfigCodec.directionWords(packed), forms);
    }

    public boolean isEmpty() { return empty; }

    private static MaterialOutputConfigData fromParts(long[] directions, long[] forms) {
        long[] packed = new long[MaterialOutputConfigCodec.WORD_COUNT];
        System.arraycopy(directions, 0, packed, 0,
                Math.min(directions.length, MaterialOutputConfigCodec.DIRECTION_WORDS));
        System.arraycopy(forms, 0, packed, MaterialOutputConfigCodec.DIRECTION_WORDS,
                Math.min(forms.length, MaterialOutputConfigCodec.FORM_WORDS));
        return fromPacked(packed);
    }

    private static boolean isPackedEmpty(long[] packed) {
        for (long word : packed) {
            if (word != 0L) return false;
        }
        return true;
    }
}
