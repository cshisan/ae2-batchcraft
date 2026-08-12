package cn.ae2bc.pattern;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Per-slot processing-pattern configuration containing output direction and output form. */
public final class MaterialOutputConfigData {
    public static final MaterialOutputConfigData EMPTY = new MaterialOutputConfigData(
            InputDirectionData.EMPTY, new long[MaterialOutputConfigCodec.FORM_WORDS]);
    public static final Codec<MaterialOutputConfigData> CODEC = Codec.LONG.listOf(
                    0, MaterialOutputConfigCodec.WORD_COUNT)
            .xmap(MaterialOutputConfigData::fromWords, MaterialOutputConfigData::words);
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialOutputConfigData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                for (long word : value.toPacked()) {
                    buffer.writeVarLong(word);
                }
            },
            buffer -> {
                long[] words = new long[MaterialOutputConfigCodec.WORD_COUNT];
                for (int i = 0; i < words.length; i++) {
                    words[i] = buffer.readVarLong();
                }
                return fromPacked(words);
            });

    private final InputDirectionData directions;
    private final long[] forms;
    private final boolean empty;

    private MaterialOutputConfigData(InputDirectionData directions, long[] forms) {
        this.directions = directions == null ? InputDirectionData.EMPTY : directions;
        this.forms = Arrays.copyOf(forms, MaterialOutputConfigCodec.FORM_WORDS);
        this.empty = this.directions.isEmpty() && !MaterialOutputConfigCodec.containsExplicitForm(this.forms);
    }

    public static MaterialOutputConfigData fromPacked(long[] packed) {
        if (packed == null || packed.length == 0) {
            return EMPTY;
        }
        var result = new MaterialOutputConfigData(
                InputDirectionData.fromPacked(MaterialOutputConfigCodec.directionWords(packed)),
                MaterialOutputConfigCodec.formWords(packed));
        return result.empty ? EMPTY : result;
    }

    private static MaterialOutputConfigData fromWords(List<Long> values) {
        long[] words = new long[MaterialOutputConfigCodec.WORD_COUNT];
        for (int i = 0; i < Math.min(words.length, values.size()); i++) {
            Long value = values.get(i);
            words[i] = value == null ? 0 : value;
        }
        return fromPacked(words);
    }

    private List<Long> words() {
        long[] packed = toPacked();
        List<Long> result = new ArrayList<>(packed.length);
        for (long word : packed) {
            result.add(word);
        }
        return result;
    }

    public long[] toPacked() {
        long[] packed = new long[MaterialOutputConfigCodec.WORD_COUNT];
        long[] directionWords = directions.toPacked();
        System.arraycopy(directionWords, 0, packed, 0,
                Math.min(directionWords.length, MaterialOutputConfigCodec.DIRECTION_WORDS));
        System.arraycopy(forms, 0, packed, MaterialOutputConfigCodec.DIRECTION_WORDS, forms.length);
        return packed;
    }

    public InputDirectionData directions() {
        return directions;
    }

    public @Nullable Direction getDirection(int slot) {
        return directions.getDirection(slot);
    }

    public MaterialOutputForm getOutputForm(int slot) {
        if (!InputDirectionData.isValidSlot(slot)) {
            return MaterialOutputForm.NORMAL;
        }
        return MaterialOutputForm.fromId(MaterialOutputConfigCodec.getOutputFormId(forms, slot));
    }

    public MaterialOutputConfigData withDirection(int slot, @Nullable Direction direction) {
        if (!InputDirectionData.isValidSlot(slot)) {
            return this;
        }
        return compact(new MaterialOutputConfigData(directions.withDirection(slot, direction), forms));
    }

    public MaterialOutputConfigData withOutputForm(int slot, MaterialOutputForm form) {
        if (!InputDirectionData.isValidSlot(slot)) {
            return this;
        }
        MaterialOutputForm safeForm = form == null ? MaterialOutputForm.NORMAL : form;
        if (getOutputForm(slot) == safeForm) {
            return this;
        }
        long[] copy = MaterialOutputConfigCodec.withOutputFormId(forms, slot, safeForm.getId());
        return compact(new MaterialOutputConfigData(directions, copy));
    }

    public MaterialOutputConfigData clearSlot(int slot) {
        return withDirection(slot, null).withOutputForm(slot, MaterialOutputForm.NORMAL);
    }

    public boolean isEmpty() {
        return empty;
    }

    private static MaterialOutputConfigData compact(MaterialOutputConfigData value) {
        return value.empty ? EMPTY : value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MaterialOutputConfigData other
                && directions.equals(other.directions) && Arrays.equals(forms, other.forms);
    }

    @Override
    public int hashCode() {
        return 31 * directions.hashCode() + Arrays.hashCode(forms);
    }
}
