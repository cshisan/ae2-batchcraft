package org.ae2bc.part.general;

import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public final class Pp2mpAdjacentTarget {
    private final Supplier<@Nullable Level> levelSupplier;
    private final Supplier<@Nullable BlockPos> posSupplier;
    private final Supplier<@Nullable Direction> sideSupplier;

    private @Nullable Target target;
    private long generation;

    public Pp2mpAdjacentTarget(
            Supplier<@Nullable Level> levelSupplier,
            Supplier<@Nullable BlockPos> posSupplier,
            Supplier<@Nullable Direction> sideSupplier
    ) {
        this.levelSupplier = levelSupplier;
        this.posSupplier = posSupplier;
        this.sideSupplier = sideSupplier;
    }

    /* 统一解析部件相邻位置；未安装的部件不提供有效目标。 */
    public static Pp2mpAdjacentTarget forPart(AEBasePart part) {
        return new Pp2mpAdjacentTarget(part::getLevel,
                () -> Pp2mpAdjacentTarget.adjacentPos(part),
                () -> {
                    Direction side = part.getSide();
                    return side == null ? null : side.getOpposite();
                });
    }

    /* 保持相邻位置不变，仅使用材料配置指定的插入面。 */
    public static Pp2mpAdjacentTarget forPart(AEBasePart part, Direction targetSide) {
        return new Pp2mpAdjacentTarget(part::getLevel,
                () -> Pp2mpAdjacentTarget.adjacentPos(part), () -> targetSide);
    }

    private static @Nullable BlockPos adjacentPos(AEBasePart part) {
        Direction side = part.getSide();
        return side == null ? null : part.getBlockEntity().getBlockPos().relative(side);
    }

    /* 每次检查目标身份，身份不变时复用快照；不保存库存内容。 */
    public @Nullable Target resolve() {
        Level currentLevel = this.levelSupplier.get();
        BlockPos currentPos = this.posSupplier.get();
        Direction currentSide = this.sideSupplier.get();
        if (!(currentLevel instanceof ServerLevel serverLevel)
                || currentPos == null
                || currentSide == null) {
            this.clear();
            return null;
        }

        Target previous = this.target;
        if (previous == null || previous.level != serverLevel
                || !previous.pos.equals(currentPos) || previous.side != currentSide) {
            this.target = new Target(serverLevel, currentPos.immutable(), currentSide, ++this.generation);
        }
        return this.target;
    }

    public void clear() {
        if (this.target != null) {
            this.generation++;
        }
        this.target = null;
    }

    /* 能力失效回调只检查已解析身份，不在回调中访问世界。 */
    public boolean isCurrent(Target target) {
        return this.target == target;
    }

    public static final class Target {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Direction side;
        private final long generation;

        private Target(ServerLevel level, BlockPos pos, Direction side, long generation) {
            this.level = level;
            this.pos = pos;
            this.side = side;
            this.generation = generation;
        }

        public <T> T withLevel(Function<ServerLevel, T> operation) {
            return operation.apply(this.level);
        }

        public BlockPos pos() {
            return this.pos;
        }

        public Direction side() {
            return this.side;
        }

        public long generation() {
            return this.generation;
        }
    }
}
