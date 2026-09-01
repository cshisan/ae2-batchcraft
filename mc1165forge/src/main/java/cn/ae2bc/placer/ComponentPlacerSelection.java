package cn.ae2bc.placer;

import cn.ae2bc.placer.ComponentSelectionGeometry.Validation;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentPlacerSelection {
    private final ResourceLocation dimension;
    private final BlockPos first;
    private final BlockPos second;

    public ComponentPlacerSelection(ResourceLocation dimension, BlockPos first, BlockPos second) {
        this.dimension = dimension;
        this.first = first.immutable();
        this.second = second == null ? null : second.immutable();
    }

    public static ComponentPlacerSelection start(RegistryKey<World> dimension, BlockPos first) {
        return new ComponentPlacerSelection(dimension.location(), first, null);
    }

    public ComponentPlacerSelection complete(BlockPos value) {
        return new ComponentPlacerSelection(dimension, first, value);
    }

    public ResourceLocation getDimension() { return dimension; }
    public BlockPos getFirst() { return first; }
    public BlockPos getSecond() { return second; }
    public boolean isComplete() { return second != null; }

    public SelectionValidation validate() {
        if (second == null) return SelectionValidation.INCOMPLETE;
        Validation result = geometry().validate();
        if (result == Validation.VALID) return SelectionValidation.VALID;
        if (result == Validation.VOLUME_NOT_ALLOWED) return SelectionValidation.VOLUME_NOT_ALLOWED;
        return SelectionValidation.TOO_LARGE;
    }

    public int sizeX() { return second == null ? 0 : size(first.getX(), second.getX()); }
    public int sizeY() { return second == null ? 0 : size(first.getY(), second.getY()); }
    public int sizeZ() { return second == null ? 0 : size(first.getZ(), second.getZ()); }

    public List<BlockPos> positions(ComponentPlacerSettings settings) {
        if (second == null) return Collections.emptyList();
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (ComponentSelectionGeometry.Position position : geometry().positions(
                settings.getOffsetX(), settings.getOffsetY(), settings.getOffsetZ())) {
            result.add(new BlockPos(position.x(), position.y(), position.z()));
        }
        return result;
    }

    public CompoundNBT write() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("dimension", dimension.toString());
        tag.putLong("first", first.asLong());
        if (second != null) tag.putLong("second", second.asLong());
        return tag;
    }

    public static ComponentPlacerSelection read(CompoundNBT tag) {
        if (tag == null || !tag.contains("dimension") || !tag.contains("first")) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return null;
        return new ComponentPlacerSelection(dimension, BlockPos.of(tag.getLong("first")),
                tag.contains("second") ? BlockPos.of(tag.getLong("second")) : null);
    }

    private ComponentSelectionGeometry geometry() {
        return new ComponentSelectionGeometry(first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ());
    }

    private static int size(int first, int second) {
        long result = Math.abs((long) first - second) + 1L;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    public enum SelectionValidation {
        VALID,
        INCOMPLETE,
        VOLUME_NOT_ALLOWED,
        TOO_LARGE
    }
}
