package org.ae2bc.ioport;

import appeng.api.orientation.RelativeSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.ioport.CellTransferMode;

public record IoPortTransferData(CellTransferMode mode, int sides) {
    public static final IoPortTransferData DEFAULT = new IoPortTransferData(CellTransferMode.SLOTS, 0);
    private static final String KEY = "ae2bc:cell_transfer";
    private static final String MODE = "mode";
    private static final String SIDES = "sides";
    private static final int SIDE_MASK = (1 << RelativeSide.values().length) - 1;

    public IoPortTransferData {
        sides &= SIDE_MASK;
    }

    public boolean enabled(RelativeSide side) {
        return (this.sides & (1 << side.ordinal())) != 0;
    }

    public IoPortTransferData nextMode() {
        return new IoPortTransferData(this.mode.next(), this.sides);
    }

    public IoPortTransferData toggle(RelativeSide side) {
        return new IoPortTransferData(this.mode, this.sides ^ (1 << side.ordinal()));
    }

    public IoPortTransferData clearSides() {
        return new IoPortTransferData(this.mode, 0);
    }

    /* 使用独立标签保存设置，不改变原生操作模式与库存数据。 */
    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MODE, this.mode.id());
        tag.putInt(SIDES, this.sides);
        root.put(KEY, tag);
    }

    public static IoPortTransferData load(CompoundTag root) {
        CompoundTag tag = root.getCompound(KEY);
        return new IoPortTransferData(CellTransferMode.fromId(tag.getString(MODE)), tag.getInt(SIDES));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(this.mode);
        buffer.writeByte(this.sides);
    }

    public static IoPortTransferData read(RegistryFriendlyByteBuf buffer) {
        return new IoPortTransferData(buffer.readEnum(CellTransferMode.class), buffer.readUnsignedByte());
    }
}
