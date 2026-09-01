package cn.ae2bc.logic;

import net.minecraft.core.Direction;

/** Cardinal directions arranged relative to the player's view when a screen opens. */
public record DirectionLayout(Direction front, Direction left, Direction right, Direction back) {
    public static DirectionLayout fromPlayerFacing(Direction facing) {
        Direction front = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        return new DirectionLayout(front, front.getCounterClockWise(), front.getClockWise(), front.getOpposite());
    }
}
