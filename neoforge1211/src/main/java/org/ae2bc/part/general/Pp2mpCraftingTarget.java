package org.ae2bc.part.general;

import appeng.api.AECapabilities;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

public final class Pp2mpCraftingTarget {
    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Runnable changed;
    private @Nullable BlockCapabilityCache<ICraftingMachine, Direction> machineCache;
    private long cachedGeneration = Long.MIN_VALUE;
    private long cacheVersion;

    public Pp2mpCraftingTarget(Pp2mpAdjacentTarget adjacentTarget, Runnable changed) {
        this.adjacentTarget = adjacentTarget;
        this.changed = changed;
    }

    /* 复用相同地址的能力缓存，能力失效后在正常调用中重新查询。 */
    public @Nullable ICraftingMachine findMachine() {
        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (target == null) {
            this.clear();
            return null;
        }

        if (this.machineCache == null || this.cachedGeneration != target.generation()) {
            long version = ++this.cacheVersion;
            this.machineCache = target.withLevel(level -> BlockCapabilityCache.create(
                    AECapabilities.CRAFTING_MACHINE,
                    level, target.pos(), target.side(),
                    () -> this.cacheVersion == version && this.adjacentTarget.isCurrent(target),
                    this.changed
            ));
            this.cachedGeneration = target.generation();
        }

        return this.machineCache.getCapability();
    }

    public boolean acceptsPlans() {
        ICraftingMachine machine = this.findMachine();
        return machine != null && machine.acceptsPlans();
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction direction) {
        ICraftingMachine machine = this.findMachine();
        return machine != null && machine.acceptsPlans()
                && machine.pushPattern(pattern, inputs, direction);
    }

    public void clear() {
        this.cacheVersion++;
        this.machineCache = null;
        this.cachedGeneration = Long.MIN_VALUE;
    }
}
