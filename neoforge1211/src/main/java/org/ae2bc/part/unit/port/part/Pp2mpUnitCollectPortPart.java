package org.ae2bc.part.unit.port.part;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.StackWorldBehaviors;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitInputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class Pp2mpUnitCollectPortPart extends Pp2mpUnitInputTypePortPart {
    private static final int MAX_ENTITY_VISITS = 256;
    protected final Pp2mpAdjacentTarget adjacentTarget;
    private List<PickupStrategy> pickupStrategies;
    private long pickupGeneration = Long.MIN_VALUE;
    private int entityCursor;

    public Pp2mpUnitCollectPortPart(IPartItem<?> partItem) {
        this(partItem, UnitPortType.COLLECT, Pp2mpUnitPortPartModel.MODEL_COLLECT);
    }

    protected Pp2mpUnitCollectPortPart(IPartItem<?> partItem, UnitPortType type, Pp2mpUnitPortPartModel models) {
        super(partItem, type, models);
        this.adjacentTarget = Pp2mpAdjacentTarget.forPart(this);
    }

    public boolean collectProducts(int maxOperations) {
        this.receiveInventory.beginBatch();
        try {
            return this.collectInScope(maxOperations);
        } finally {
            this.receiveInventory.endBatch();
        }
    }

    /* 逐实体重置拾取策略，避免一次拒收或部分接收阻塞后续产物。 */
    private boolean collectInScope(int maxOperations) {
        Pp2mpUnitManagerPart manager = this.findManager();
        if (manager == null || !this.isBoundTo(manager) || !manager.getLogic().isTaskOperational() || maxOperations <= 0) {
            return false;
        }

        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (target == null) {
            return false;
        }

        List<PickupStrategy> strategies = this.getPickupStrategies(target);
        if (strategies.isEmpty()) {
            return false;
        }
        IGrid grid = this.grid();
        if (grid == null) {
            return false;
        }

        boolean changed = false;
        int operations = 0;
        AABB area = new AABB(target.pos());
        // shortcut: 世界实体查询仍枚举区域；本预算只限制拾取检查，实体查询成为热点时再改。
        List<ItemEntity> entities = target.withLevel(
                level -> level.getEntitiesOfClass(ItemEntity.class, area)
        );
        int size = entities.size();
        int start = size == 0 ? 0 : Math.floorMod(this.entityCursor, size);
        int attempts = Math.min(size, Pp2mpUnitCollectPortPart.MAX_ENTITY_VISITS);
        for (int visited = 0; visited < attempts && operations < maxOperations; visited++) {
            int index = (start + visited) % size;
            this.entityCursor = (index + 1) % size;
            ItemEntity entity = entities.get(index);
            if (!entity.isAlive()) {
                continue;
            }
            int before = entity.getItem().getCount();
            for (PickupStrategy strategy : strategies) {
                strategy.reset();
                if (!strategy.canPickUpEntity(entity)) {
                    continue;
                }
                strategy.pickUpEntity(grid.getEnergyService(), this::insertCollected, entity);
                break;
            }
            if (!entity.isAlive() || entity.getItem().getCount() < before) {
                changed = true;
                operations++;
            }
        }

        if (operations < maxOperations) {
            changed |= this.tryPickupFluid(target, grid);
        }
        return changed;
    }

    protected boolean tryPickupFluid(Pp2mpAdjacentTarget.Target target, IGrid grid) {
        boolean hasFluid = target.withLevel(level -> {
            BlockState state = level.getBlockState(target.pos());
            return !state.getFluidState().isEmpty();
        });
        if (!hasFluid) {
            return false;
        }

        return this.tryPickupBlock(target, grid);
    }

    protected boolean tryPickupBlock(Pp2mpAdjacentTarget.Target target, IGrid grid) {
        this.receiveInventory.beginBatch();
        try {
            return this.pickupInScope(target, grid, this::insertCollected);
        } finally {
            this.receiveInventory.endBatch();
        }
    }

    protected boolean pickupInScope(Pp2mpAdjacentTarget.Target target, IGrid grid, PickupSink sink) {
        List<PickupStrategy> strategies = this.getPickupStrategies(target);
        strategies.forEach(PickupStrategy::reset);
        for (PickupStrategy strategy : strategies) {
            if (strategy.tryPickup(grid.getEnergyService(), sink) == PickupStrategy.Result.PICKED_UP) {
                return true;
            }
        }
        return false;
    }

    protected ItemEnchantments getPickupEnchantments() {
        return ItemEnchantments.EMPTY;
    }

    protected List<PickupStrategy> getPickupStrategies(Pp2mpAdjacentTarget.Target target) {
        if (this.pickupStrategies == null || this.pickupGeneration != target.generation()) {
            IGridNode node = this.getMainNode().getNode();
            UUID owner = node == null ? null : node.getOwningPlayerProfileId();
            this.pickupStrategies = this.createPickupStrategies(target, owner);
            this.pickupGeneration = target.generation();
        }
        return this.pickupStrategies;
    }

    protected List<PickupStrategy> createPickupStrategies(Pp2mpAdjacentTarget.Target target, @Nullable UUID owner) {
        return target.withLevel(level -> StackWorldBehaviors.createPickupStrategies(
                level, target.pos(), target.side(), this.getBlockEntity(),
                this.getPickupEnchantments(), owner
        ));
    }

    protected void clearPickupStrategies() {
        this.pickupStrategies = null;
        this.pickupGeneration = Long.MIN_VALUE;
        this.entityCursor = 0;
    }

    protected long insertCollected(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !this.allowsReceived(key)) {
            return 0;
        }
        return this.receiveInventory.insert(key, amount, mode, this.getActionSource());
    }

    @Override
    protected void onAdjacentTargetChanged() {
        this.adjacentTarget.clear();
        this.clearPickupStrategies();
        this.alertManager();
    }
}
