package cn.ae2bc.placer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ComponentPlacerSelection(
        ResourceLocation dimension,
        BlockPos first,
        @Nullable BlockPos second) {
    public static final Codec<ComponentPlacerSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(ComponentPlacerSelection::dimension),
            BlockPos.CODEC.fieldOf("first").forGetter(ComponentPlacerSelection::first),
            BlockPos.CODEC.optionalFieldOf("second")
                    .forGetter(selection -> Optional.ofNullable(selection.second))
    ).apply(instance, (dimension, first, second) ->
            new ComponentPlacerSelection(dimension, first, second.orElse(null))));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerSelection> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeResourceLocation(value.dimension);
                buffer.writeBlockPos(value.first);
                buffer.writeBoolean(value.second != null);
                if (value.second != null) {
                    buffer.writeBlockPos(value.second);
                }
            },
            buffer -> new ComponentPlacerSelection(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBoolean() ? buffer.readBlockPos() : null));

    public ComponentPlacerSelection {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(first, "first");
        first = first.immutable();
        second = second == null ? null : second.immutable();
    }

    public static ComponentPlacerSelection start(ResourceLocation dimension, BlockPos first) {
        return new ComponentPlacerSelection(dimension, first, null);
    }

    public ComponentPlacerSelection complete(BlockPos oppositeCorner) {
        return new ComponentPlacerSelection(dimension, first, oppositeCorner);
    }

    public Validation validate() {
        if (second == null) {
            return Validation.INCOMPLETE;
        }

        return switch (geometry().validate()) {
            case VALID -> Validation.VALID;
            case VOLUME_NOT_ALLOWED -> Validation.VOLUME_NOT_ALLOWED;
            case TOO_LARGE -> Validation.TOO_LARGE;
        };
    }

    public int sizeX() {
        return second == null ? 0 : saturatedSize(second.getX(), first.getX());
    }

    public int sizeY() {
        return second == null ? 0 : saturatedSize(second.getY(), first.getY());
    }

    public int sizeZ() {
        return second == null ? 0 : saturatedSize(second.getZ(), first.getZ());
    }

    public List<BlockPos> positions(ComponentPlacerSettings settings) {
        if (second == null) {
            return List.of();
        }
        ComponentSelectionGeometry geometry = geometry();
        return geometry.positions(settings.offsetX(), settings.offsetY(), settings.offsetZ()).stream()
                .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                .toList();
    }

    private ComponentSelectionGeometry geometry() {
        if (second == null) {
            throw new IllegalStateException("Selection is incomplete");
        }
        return new ComponentSelectionGeometry(
                first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ());
    }

    private static int saturatedSize(int first, int second) {
        long size = Math.abs((long) first - second) + 1;
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    public enum Validation {
        VALID,
        INCOMPLETE,
        VOLUME_NOT_ALLOWED,
        TOO_LARGE
    }
}
