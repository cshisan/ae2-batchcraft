package cn.ae2bc.placer;

import cn.ae2bc.core.ProjectLimits;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;

public final class ComponentPlacerSettings {
    public static final int MAX_OFFSET = ProjectLimits.COMPONENT_PLACER_MAX_OFFSET;
    public static final ComponentPlacerSettings DEFAULT = new ComponentPlacerSettings(Direction.UP, 0, 1, 0);

    private final Direction direction;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    public ComponentPlacerSettings(Direction direction, int offsetX, int offsetY, int offsetZ) {
        this.direction = direction == null ? Direction.UP : direction;
        this.offsetX = clampOffset(offsetX);
        this.offsetY = clampOffset(offsetY);
        this.offsetZ = clampOffset(offsetZ);
    }

    public Direction getDirection() { return direction; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }
    public int getOffsetZ() { return offsetZ; }

    public ComponentPlacerSettings withDirection(Direction value) {
        return new ComponentPlacerSettings(value, offsetX, offsetY, offsetZ);
    }

    public ComponentPlacerSettings withOffsets(int x, int y, int z) {
        return new ComponentPlacerSettings(direction, x, y, z);
    }

    public ComponentPlacerSettings resetOffsets() {
        return withOffsets(0, 1, 0);
    }

    public CompoundNBT write() {
        CompoundNBT tag = new CompoundNBT();
        tag.putByte("direction", (byte) direction.ordinal());
        tag.putInt("offset_x", offsetX);
        tag.putInt("offset_y", offsetY);
        tag.putInt("offset_z", offsetZ);
        return tag;
    }

    public static ComponentPlacerSettings read(CompoundNBT tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        int ordinal = tag.getByte("direction") & 0xFF;
        Direction direction = Direction.values()[ordinal % Direction.values().length];
        return new ComponentPlacerSettings(direction, tag.getInt("offset_x"),
                tag.contains("offset_y") ? tag.getInt("offset_y") : 1, tag.getInt("offset_z"));
    }

    public static int clampOffset(int value) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }
}
