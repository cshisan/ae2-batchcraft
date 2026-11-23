package org.ae2bc.client.screen.component;

import net.minecraft.core.Direction;

public record DirectionLayout(Direction front, Direction left, Direction right, Direction back) {
    public static DirectionLayout fromPlayerFacing(Direction facing) {
        Direction front = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        return new DirectionLayout(front, front.getCounterClockWise(), front.getClockWise(), front.getOpposite());
    }
}
