package org.ae2bc.part.general;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKeyType;
import appeng.parts.automation.StackWorldBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("UnstableApiUsage")
public final class Pp2mpReceiveTarget {
    private final Supplier<@Nullable IGrid> gridSupplier;
    private final Supplier<Short> frequencySupplier;
    private @Nullable Map<AEKeyType, ExternalStorageStrategy> storageStrategies;
    private @Nullable ServerLevel storageLevel;
    private @Nullable BlockPos storageTargetPos;
    private @Nullable Direction storageTargetSide;

    public Pp2mpReceiveTarget(Supplier<@Nullable IGrid> gridSupplier, Supplier<Short> frequencySupplier) {
        this.gridSupplier = gridSupplier;
        this.frequencySupplier = frequencySupplier;
    }

    public @Nullable GenericInternalInventory findInventory() {
        this.refreshCacheIfNeeded();
        if (this.storageLevel == null || this.storageTargetPos == null || this.storageTargetSide == null) {
            return null;
        }
        return this.storageLevel.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV,
                this.storageTargetPos, this.storageTargetSide
        );
    }

    /* 仅外部库存回退路径需要策略；通用库存返回不额外登记能力监听器。 */
    public Map<AEKeyType, ExternalStorageStrategy> findStrategies() {
        this.refreshCacheIfNeeded();
        ServerLevel level = this.storageLevel;
        BlockPos pos = this.storageTargetPos;
        Direction side = this.storageTargetSide;
        if (level != null && pos != null && side != null && this.storageStrategies == null) {
            this.storageStrategies = StackWorldBehaviors.createExternalStorageStrategies(level, pos, side);
        }
        return this.storageStrategies == null ? Collections.emptyMap() : this.storageStrategies;
    }

    public void clear() {
        this.storageStrategies = null;
        this.storageLevel = null;
        this.storageTargetPos = null;
        this.storageTargetSide = null;
    }

    private void refreshCacheIfNeeded() {
        IGrid grid = this.gridSupplier.get();
        Short frequency = this.frequencySupplier.get();
        if (grid == null || frequency == null || frequency == 0) {
            this.clear();
            return;
        }

        Pp2mpInputPart input = Pp2mpGridService.get(grid).findInput(frequency);
        Direction targetSide = input == null ? null : input.getSide();
        if (input == null || targetSide == null || !(input.getLevel() instanceof ServerLevel level)) {
            this.clear();
            return;
        }

        BlockPos targetPos = input.getBlockEntity().getBlockPos().relative(targetSide);

        if (this.storageLevel != level
                || this.storageTargetPos == null
                || !this.storageTargetPos.equals(targetPos)
                || this.storageTargetSide != targetSide
        ) {
            // 通用库存可处理的返回不创建外部策略；目标变化后按需重建。
            this.storageStrategies = null;
            this.storageLevel = level;
            this.storageTargetPos = targetPos.immutable();
            this.storageTargetSide = targetSide;
        }
    }
}
