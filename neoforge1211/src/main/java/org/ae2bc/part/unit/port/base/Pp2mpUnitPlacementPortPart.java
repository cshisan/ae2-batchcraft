package org.ae2bc.part.unit.port.base;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.StackWorldBehaviors;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.core.unit.UnitPortType;
import org.jetbrains.annotations.Nullable;

/**
 * 向世界中放置材料的单元端口共用逻辑。
 */
@SuppressWarnings("UnstableApiUsage")
public abstract class Pp2mpUnitPlacementPortPart extends Pp2mpUnitOutputTypePortPart {
    private final boolean drop;
    private @Nullable PlacementStrategy placementStrategy;

    protected Pp2mpUnitPlacementPortPart(
            IPartItem<?> partItem,
            UnitPortType type,
            Pp2mpUnitPortPartModel models,
            boolean drop
    ) {
        super(partItem, type, models);
        this.drop = drop;
    }

    /* 按本端口的放置行为放置管理器派发的材料。 */
    @Override
    protected long outputToTarget(AEKey what, long amount, Actionable mode) {
        return this.placementStrategy().placeInWorld(what, amount, mode, this.drop);
    }

    /* 相邻世界目标变化后重新创建操作策略。 */
    private PlacementStrategy placementStrategy() {
        Direction side = this.getSide();
        IGridNode node = this.getMainNode().getNode();
        if (this.placementStrategy == null
                && this.getLevel() instanceof ServerLevel level
                && side != null
                && node != null) {
            this.placementStrategy = StackWorldBehaviors.createPlacementStrategies(
                    level,
                    this.getBlockEntity().getBlockPos().relative(side),
                    side.getOpposite(),
                    this.getBlockEntity(),
                    node.getOwningPlayerProfileId()
            );
        }
        return this.placementStrategy == null ? PlacementStrategy.noop() : this.placementStrategy;
    }

    /* 邻接方块或节点变化后重新解析放置目标与操作玩家。 */
    @Override
    protected void onAdjacentTargetChanged() {
        this.placementStrategy = null;
    }
}
