package org.ae2bc.placer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.ae2bc.core.placer.SelectionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ComponentPlacerSelection(ResourceLocation dimension, BlockPos first, @Nullable BlockPos second) {
    public static final Codec<ComponentPlacerSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(ComponentPlacerSelection::dimension),
            BlockPos.CODEC.fieldOf("first").forGetter(ComponentPlacerSelection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(selection -> Optional.ofNullable(selection.second()))
    ).apply(instance, (dimension, first, second) -> new ComponentPlacerSelection(dimension, first, second.orElse(null))));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPlacerSelection> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeResourceLocation(value.dimension());
                buffer.writeBlockPos(value.first());
                buffer.writeBoolean(value.second() != null);
                if (value.second() != null) buffer.writeBlockPos(value.second());
            },
            buffer -> new ComponentPlacerSelection(buffer.readResourceLocation(), buffer.readBlockPos(),
                    buffer.readBoolean() ? buffer.readBlockPos() : null)
    );
    private static final int MAX_AXIS_SIZE = 16;
    private static final int MAX_TARGETS = MAX_AXIS_SIZE * MAX_AXIS_SIZE;

    public ComponentPlacerSelection {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(first, "first");
        first = first.immutable();
        second = second == null ? null : second.immutable();
    }

    public static ComponentPlacerSelection start(ResourceLocation dimension, BlockPos first) {
        return new ComponentPlacerSelection(dimension, first, null);
    }

    public ComponentPlacerSelection complete(BlockPos last) {
        return new ComponentPlacerSelection(this.dimension, this.first, last);
    }

    public SelectionStatus validate() {
        if (this.second == null) return SelectionStatus.INCOMPLETE;
        int varying = (this.first.getX() == this.second.getX() ? 0 : 1)
                + (this.first.getY() == this.second.getY() ? 0 : 1)
                + (this.first.getZ() == this.second.getZ() ? 0 : 1);
        if (varying == 3) return SelectionStatus.SELECTION_NOT_ALLOWED;
        long sx = this.size(this.first.getX(), this.second.getX());
        long sy = this.size(this.first.getY(), this.second.getY());
        long sz = this.size(this.first.getZ(), this.second.getZ());
        return sx > MAX_AXIS_SIZE || sy > MAX_AXIS_SIZE || sz > MAX_AXIS_SIZE || sx * sy * sz > MAX_TARGETS
                ? SelectionStatus.TOO_LARGE : SelectionStatus.VALID;
    }

    public int sizeX() {
        return this.second == null ? 0 : (int) this.size(this.first.getX(), this.second.getX());
    }

    public int sizeY() {
        return this.second == null ? 0 : (int) this.size(this.first.getY(), this.second.getY());
    }

    public int sizeZ() {
        return this.second == null ? 0 : (int) this.size(this.first.getZ(), this.second.getZ());
    }

    public List<BlockPos> positions(ComponentPlacerPose settings) {
        if (this.second == null || this.validate() != SelectionStatus.VALID) {
            return List.of();
        }
        int minX = Math.min(this.first.getX(), this.second.getX()) + settings.pos().x();
        int minY = Math.min(this.first.getY(), this.second.getY()) + settings.pos().y();
        int minZ = Math.min(this.first.getZ(), this.second.getZ()) + settings.pos().z();
        int maxX = Math.max(this.first.getX(), this.second.getX()) + settings.pos().x();
        int maxY = Math.max(this.first.getY(), this.second.getY()) + settings.pos().y();
        int maxZ = Math.max(this.first.getZ(), this.second.getZ()) + settings.pos().z();
        List<BlockPos> result = new ArrayList<>(this.sizeX() * this.sizeY() * this.sizeZ());
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) {
                    result.add(new BlockPos(x, y, z));
                }
        return List.copyOf(result);
    }

    private long size(int firstValue, int secondValue) {
        return Math.abs((long) firstValue - secondValue) + 1L;
    }
}
