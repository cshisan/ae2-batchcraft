package cn.ae2bc.logic;

import net.minecraft.util.EnumFacing;

/** Player-relative horizontal directions used by the material output screen. */
public final class DirectionLayout {
    private final EnumFacing front;
    private final EnumFacing left;
    private final EnumFacing right;
    private final EnumFacing back;

    private DirectionLayout(EnumFacing front) {
        this.front = front;
        this.left = front.rotateYCCW();
        this.right = front.rotateY();
        this.back = front.getOpposite();
    }

    public static DirectionLayout fromPlayerFacing(EnumFacing facing) {
        EnumFacing front = facing == null || facing.getAxis().isVertical() ? EnumFacing.NORTH : facing;
        return new DirectionLayout(front);
    }

    public EnumFacing front() { return front; }
    public EnumFacing left() { return left; }
    public EnumFacing right() { return right; }
    public EnumFacing back() { return back; }
}
