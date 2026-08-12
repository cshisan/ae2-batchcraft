package cn.ae2bc.pattern;

import appeng.crafting.pattern.AEProcessingPattern;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compact, fault-tolerant per-slot input directions for processing patterns. */
public final class InputDirectionData {
    static final int WORD_COUNT = InputDirectionCodec.WORD_COUNT;
    private static final Direction[] DIRECTIONS = Direction.values();

    public static final InputDirectionData EMPTY = new InputDirectionData(new long[WORD_COUNT]);
    public static final Codec<InputDirectionData> CODEC = Codec.LONG.listOf(0, WORD_COUNT)
            .xmap(InputDirectionData::fromWords, InputDirectionData::words);
    public static final StreamCodec<RegistryFriendlyByteBuf, InputDirectionData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                for (long word : value.packed) {
                    buffer.writeVarLong(word);
                }
            },
            buffer -> {
                long[] words = new long[WORD_COUNT];
                for (int i = 0; i < words.length; i++) {
                    words[i] = buffer.readVarLong();
                }
                return fromPacked(words);
            });

    private final long[] packed;
    private final boolean hasExplicitDirections;

    private InputDirectionData(long[] packed) {
        this.packed = Arrays.copyOf(packed, WORD_COUNT);
        this.hasExplicitDirections = containsExplicitDirection(this.packed);
    }

    public static InputDirectionData fromPacked(long[] packed) {
        if (packed == null || packed.length == 0) {
            return EMPTY;
        }
        var result = new InputDirectionData(packed);
        return result.hasExplicitDirections ? result : EMPTY;
    }

    private static InputDirectionData fromWords(List<Long> words) {
        long[] packed = new long[WORD_COUNT];
        int wordCount = Math.min(words.size(), packed.length);
        for (int i = 0; i < wordCount; i++) {
            Long word = words.get(i);
            packed[i] = word == null ? 0 : word;
        }
        return fromPacked(packed);
    }

    private List<Long> words() {
        List<Long> result = new ArrayList<>(WORD_COUNT);
        for (long word : packed) {
            result.add(word);
        }
        return result;
    }

    public long[] toPacked() {
        return packed.clone();
    }

    public int getCode(int slot) {
        return InputDirectionCodec.getCode(packed, slot);
    }

    public @Nullable Direction getDirection(int slot) {
        int ordinal = InputDirectionCodec.getDirectionOrdinal(packed, slot);
        return ordinal >= 0 ? DIRECTIONS[ordinal] : null;
    }

    public InputDirectionData withDirection(int slot, @Nullable Direction direction) {
        return withCode(slot, direction == null ? 0 : direction.ordinal() + 1);
    }

    public InputDirectionData withCode(int slot, int code) {
        if (!isValidSlot(slot)) {
            return this;
        }
        int safeCode = InputDirectionCodec.sanitizeCode(code);
        if (getCode(slot) == safeCode) {
            return this;
        }
        long[] copy = InputDirectionCodec.withCode(packed, slot, code);
        return fromPacked(copy);
    }

    public boolean hasExplicitDirections() {
        return hasExplicitDirections;
    }

    private static boolean containsExplicitDirection(long[] packed) {
        for (int slot = 0; slot < AEProcessingPattern.MAX_INPUT_SLOTS; slot++) {
            int code = InputDirectionCodec.getCode(packed, slot);
            if (code >= 1 && code <= InputDirectionCodec.DIRECTION_COUNT) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return !hasExplicitDirections;
    }

    public static boolean isValidSlot(int slot) {
        return InputDirectionCodec.isValidSlot(slot);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof InputDirectionData other && Arrays.equals(packed, other.packed);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(packed);
    }
}
