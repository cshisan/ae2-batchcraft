package cn.ae2bc.placer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentPlacerSelection {
    private final int dimension;
    private final BlockPos first;
    private final BlockPos second;
    public ComponentPlacerSelection(int dimension, BlockPos first, BlockPos second) {
        this.dimension = dimension; this.first = first.toImmutable();
        this.second = second == null ? null : second.toImmutable();
    }
    public static ComponentPlacerSelection start(int dimension, BlockPos first) {
        return new ComponentPlacerSelection(dimension, first, null);
    }
    public ComponentPlacerSelection complete(BlockPos value) {
        return new ComponentPlacerSelection(dimension, first, value);
    }
    public int getDimension() { return dimension; }
    public BlockPos getFirst() { return first; }
    public BlockPos getSecond() { return second; }
    public boolean isComplete() { return second != null; }
    public SelectionValidation validate() {
        if (second == null) return SelectionValidation.INCOMPLETE;
        ComponentSelectionGeometry.Validation value = geometry().validate();
        if (value == ComponentSelectionGeometry.Validation.VALID) return SelectionValidation.VALID;
        if (value == ComponentSelectionGeometry.Validation.VOLUME_NOT_ALLOWED) return SelectionValidation.VOLUME_NOT_ALLOWED;
        return SelectionValidation.TOO_LARGE;
    }
    public int sizeX() { return second == null ? 0 : size(first.getX(), second.getX()); }
    public int sizeY() { return second == null ? 0 : size(first.getY(), second.getY()); }
    public int sizeZ() { return second == null ? 0 : size(first.getZ(), second.getZ()); }
    public List<BlockPos> positions(ComponentPlacerSettings settings) {
        if (second == null) return Collections.emptyList();
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (ComponentSelectionGeometry.Position value : geometry().positions(
                settings.getOffsetX(), settings.getOffsetY(), settings.getOffsetZ())) {
            result.add(new BlockPos(value.x(), value.y(), value.z()));
        }
        return result;
    }
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dimension", dimension);
        tag.setLong("first", first.toLong());
        if (second != null) tag.setLong("second", second.toLong());
        return tag;
    }
    public static ComponentPlacerSelection read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("dimension") || !tag.hasKey("first")) return null;
        return new ComponentPlacerSelection(tag.getInteger("dimension"), BlockPos.fromLong(tag.getLong("first")),
                tag.hasKey("second") ? BlockPos.fromLong(tag.getLong("second")) : null);
    }
    private ComponentSelectionGeometry geometry() {
        return new ComponentSelectionGeometry(first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ());
    }
    private static int size(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, Math.abs((long) a - b) + 1L); }
    public enum SelectionValidation { VALID, INCOMPLETE, VOLUME_NOT_ALLOWED, TOO_LARGE }
}
