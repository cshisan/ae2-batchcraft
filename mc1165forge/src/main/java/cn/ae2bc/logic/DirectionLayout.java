package cn.ae2bc.logic;

import net.minecraft.util.Direction;

/** Player-relative horizontal directions used by the material output screen. */
public final class DirectionLayout {
    private final Direction front;
    private final Direction left;
    private final Direction right;
    private final Direction back;

    private DirectionLayout(Direction front) {
        this.front = front;
        this.left = front.getCounterClockWise();
        this.right = front.getClockWise();
        this.back = front.getOpposite();
    }

    public static DirectionLayout fromPlayerFacing(Direction facing) {
        Direction front = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
        return new DirectionLayout(front);
    }

    public Direction front() { return front; }
    public Direction left() { return left; }
    public Direction right() { return right; }
    public Direction back() { return back; }
}
