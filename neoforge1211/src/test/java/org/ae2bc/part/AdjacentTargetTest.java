package org.ae2bc.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpCraftingTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

class AdjacentTargetTest {
    @Test
    void snapshotIdentityChangesForDimensionDirectionPositionAndDetachOnly() {
        AtomicReference<ServerLevel> level = new AtomicReference<>(Mockito.mock(ServerLevel.class));
        AtomicReference<BlockPos> pos = new AtomicReference<>(BlockPos.ZERO);
        AtomicReference<Direction> side = new AtomicReference<>(Direction.NORTH);
        Pp2mpAdjacentTarget adjacent = new Pp2mpAdjacentTarget(level::get, pos::get, side::get);
        Pp2mpAdjacentTarget.Target first = Objects.requireNonNull(adjacent.resolve());
        Assertions.assertSame(first, adjacent.resolve());
        level.set(Mockito.mock(ServerLevel.class));
        Pp2mpAdjacentTarget.Target second = Objects.requireNonNull(adjacent.resolve());
        Assertions.assertNotSame(first, second);
        Assertions.assertFalse(adjacent.isCurrent(first));
        side.set(Direction.SOUTH);
        Pp2mpAdjacentTarget.Target third = Objects.requireNonNull(adjacent.resolve());
        Assertions.assertNotSame(second, third);
        pos.set(BlockPos.ZERO.east());
        Pp2mpAdjacentTarget.Target fourth = Objects.requireNonNull(adjacent.resolve());
        Assertions.assertNotSame(third, fourth);
        level.set(null);
        Assertions.assertNull(adjacent.resolve());
        Assertions.assertFalse(adjacent.isCurrent(fourth));
        level.set(Mockito.mock(ServerLevel.class));
        Assertions.assertTrue(Objects.requireNonNull(adjacent.resolve()).generation() > fourth.generation());
    }

    @Test
    void detachedTargetExposesNeitherStorageNorCraftingMachine() {
        Pp2mpAdjacentTarget adjacent = new Pp2mpAdjacentTarget(
                () -> null, () -> BlockPos.ZERO, () -> Direction.NORTH);
        Pp2mpSendTarget storage = new Pp2mpSendTarget(adjacent, () -> {
        });
        Pp2mpCraftingTarget machine = new Pp2mpCraftingTarget(adjacent, () -> {
        });

        Assertions.assertNull(adjacent.resolve());
        Assertions.assertTrue(storage.findStorages().isEmpty());
        Assertions.assertNull(machine.findMachine());
        Assertions.assertFalse(machine.acceptsPlans());
        adjacent.clear();
        Assertions.assertTrue(storage.findStorages().isEmpty());
    }
}
