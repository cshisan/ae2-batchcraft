package cn.ae2bc.placer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public final class ComponentPlacerSettings {
    public static final int MAX_OFFSET = 16;
    public static final ComponentPlacerSettings DEFAULT = new ComponentPlacerSettings(EnumFacing.UP, 0, 1, 0);
    private final EnumFacing direction;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    public ComponentPlacerSettings(EnumFacing direction, int offsetX, int offsetY, int offsetZ) {
        this.direction = direction == null ? EnumFacing.UP : direction;
        this.offsetX = clampOffset(offsetX);
        this.offsetY = clampOffset(offsetY);
        this.offsetZ = clampOffset(offsetZ);
    }
    public EnumFacing getDirection() { return direction; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }
    public int getOffsetZ() { return offsetZ; }
    public ComponentPlacerSettings withDirection(EnumFacing value) {
        return new ComponentPlacerSettings(value, offsetX, offsetY, offsetZ);
    }
    public ComponentPlacerSettings withOffsets(int x, int y, int z) {
        return new ComponentPlacerSettings(direction, x, y, z);
    }
    public ComponentPlacerSettings resetOffsets() { return withOffsets(0, 1, 0); }
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("direction", (byte) direction.ordinal());
        tag.setInteger("offset_x", offsetX);
        tag.setInteger("offset_y", offsetY);
        tag.setInteger("offset_z", offsetZ);
        return tag;
    }
    public static ComponentPlacerSettings read(NBTTagCompound tag) {
        if (tag == null || tag.getKeySet().isEmpty()) return DEFAULT;
        EnumFacing direction = EnumFacing.values()[(tag.getByte("direction") & 0xFF) % EnumFacing.values().length];
        return new ComponentPlacerSettings(direction, tag.getInteger("offset_x"),
                tag.hasKey("offset_y") ? tag.getInteger("offset_y") : 1, tag.getInteger("offset_z"));
    }
    public static int clampOffset(int value) { return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value)); }
}
