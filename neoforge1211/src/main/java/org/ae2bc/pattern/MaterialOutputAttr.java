package org.ae2bc.pattern;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.ae2bc.core.pattern.DirectionHelper;
import org.ae2bc.core.pattern.MaterialOutputAttrHelper;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MaterialOutputAttr {
    public static final MaterialOutputAttr EMPTY = new MaterialOutputAttr(
            new long[MaterialOutputAttrHelper.WORD_COUNT]
    );
    public static final Codec<MaterialOutputAttr> CODEC = Codec.LONG
            .listOf(0, MaterialOutputAttrHelper.WORD_COUNT)
            .xmap(MaterialOutputAttr::fromList, MaterialOutputAttr::toList);
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialOutputAttr> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                long[] packed = value.toPacked();
                for (long word : packed) {
                    buffer.writeVarLong(word);
                }
            },
            buffer -> {
                long[] packed = new long[MaterialOutputAttrHelper.WORD_COUNT];
                for (int index = 0; index < packed.length; index++) {
                    packed[index] = buffer.readVarLong();
                }
                return MaterialOutputAttr.fromPacked(packed);
            }
    );
    private final long[] packed;

    private MaterialOutputAttr(long[] packed) {
        this.packed = MaterialOutputAttrHelper.normalize(packed);
    }

    public static MaterialOutputAttr fromPacked(long[] packed) {
        if (packed == null || packed.length == 0) {
            return EMPTY;
        }
        if (MaterialOutputAttr.hasExplicitDirections(packed) || MaterialOutputAttrHelper.containsExplicitMode(packed, MaterialOutputAttrHelper.DIRECTION_WORDS)) {
            return new MaterialOutputAttr(packed);
        }
        return EMPTY;
    }

    private static boolean hasExplicitDirections(long[] packed) {
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            if (DirectionHelper.getOrdinal(packed, slot) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static MaterialOutputAttr fromList(List<Long> values) {
        long[] packed = new long[MaterialOutputAttrHelper.WORD_COUNT];
        for (int index = 0; index < Math.min(values.size(), packed.length); index++) {
            Long value = values.get(index);
            packed[index] = value == null ? 0 : value;
        }
        return MaterialOutputAttr.fromPacked(packed);
    }

    private List<Long> toList() {
        List<Long> result = new ArrayList<>(this.packed.length);
        for (long word : this.packed) {
            result.add(word);
        }
        return result;
    }

    public long[] toPacked() {
        return Arrays.copyOf(this.packed, this.packed.length);
    }

    public @Nullable Direction direction(int slot) {
        int ordinal = DirectionHelper.getOrdinal(this.packed, slot);
        return ordinal < 0 ? null : Direction.values()[ordinal];
    }

    public boolean hasExplicitDirections() {
        return MaterialOutputAttr.hasExplicitDirections(this.packed);
    }

    public MaterialOutputMode mode(int slot) {
        int modeId = MaterialOutputAttrHelper.getModeId(
                this.packed, slot, MaterialOutputAttrHelper.DIRECTION_WORDS
        );
        return MaterialOutputMode.fromId(modeId);
    }

    public MaterialOutputAttr withMode(int slot, MaterialOutputMode mode) {
        MaterialOutputMode safeMode = mode == null ? MaterialOutputMode.NORMAL : mode;
        long[] modes = MaterialOutputAttrHelper.withModeId(
                MaterialOutputAttrHelper.modeWords(this.packed), slot, safeMode.id()
        );
        long[] updated = MaterialOutputAttrHelper.normalize(this.packed);
        System.arraycopy(modes, 0, updated, MaterialOutputAttrHelper.DIRECTION_WORDS, modes.length);
        return MaterialOutputAttr.fromPacked(updated);
    }

    public MaterialOutputAttr withDirection(int slot, @Nullable Direction direction) {
        if (DirectionHelper.invalidSlot(slot)) {
            return this;
        }
        long[] packed = Arrays.copyOf(this.packed, this.packed.length);
        long[] words = DirectionHelper.withCode(
                MaterialOutputAttrHelper.directionWords(this.packed),
                slot,
                direction == null ? 0 : direction.ordinal() + 1
        );
        System.arraycopy(
                words, 0, packed, 0,
                Math.min(words.length, MaterialOutputAttrHelper.DIRECTION_WORDS)
        );
        return MaterialOutputAttr.fromPacked(packed);
    }

    public boolean isEmpty() {
        for (long word : this.packed) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MaterialOutputAttr data && Arrays.equals(this.packed, data.packed);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.packed);
    }
}
