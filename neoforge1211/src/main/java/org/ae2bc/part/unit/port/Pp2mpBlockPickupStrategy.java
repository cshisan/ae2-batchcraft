package org.ae2bc.part.unit.port;

import appeng.api.behaviors.PickupSink;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.core.AppEng;
import appeng.core.network.clientbound.BlockTransitionEffectPacket;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class Pp2mpBlockPickupStrategy extends ItemPickupStrategy {
    private final Pp2mpAdjacentTarget.Target target;
    private final @Nullable UUID owner;

    public Pp2mpBlockPickupStrategy(ServerLevel level, Pp2mpAdjacentTarget.Target target, BlockEntity host,
                                   ItemEnchantments enchantments, @Nullable UUID owner) {
        super(level, target.pos(), target.side(), host, enchantments, owner);
        this.target = target;
        this.owner = owner;
    }

    /* 复用 AE2 的附魔工具和能耗计算，只拾取本次破坏新产生的实体。 */
    @Override
    public Result tryPickup(@NonNull IEnergySource energy, @NonNull PickupSink sink) {
        return this.target.withLevel(level -> this.breakBlock(level, energy, sink));
    }

    private Result breakBlock(ServerLevel level, IEnergySource energy, PickupSink sink) {
        BlockPos pos = this.target.pos();
        if (!level.isLoaded(pos)) {
            return Result.CANT_PICKUP;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0
                || ItemPickupStrategy.isBlockBlacklisted(state.getBlock())
                || !level.mayInteract(Platform.getFakePlayer(level, this.owner), pos)) {
            return Result.CANT_PICKUP;
        }

        List<ItemStack> drops = this.obtainBlockDrops(level, pos);
        float requiredPower = this.calculateEnergyUsage(level, pos, drops);
        boolean canStore = drops.isEmpty() || drops.stream().anyMatch(
                stack -> sink.insert(AEItemKey.of(stack), stack.getCount(), Actionable.SIMULATE) == stack.getCount());
        if (!canStore || energy.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                <= requiredPower - 0.1) {
            return Result.CANT_STORE;
        }

        AABB area = new AABB(pos).inflate(0.2);
        Set<ItemEntity> existing = new HashSet<>(level.getEntitiesOfClass(ItemEntity.class, area));
        if (!level.destroyBlock(pos, false)) {
            return Result.CANT_PICKUP;
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!existing.contains(entity)) {
                super.pickUpEntity(energy, sink, entity);
            }
        }
        for (ItemStack stack : drops) {
            int inserted = (int) sink.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE);
            if (inserted < stack.getCount()) {
                Platform.spawnDrops(level, pos, List.of(stack.copyWithCount(stack.getCount() - inserted)));
            }
        }
        energy.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        AppEng.instance().sendToAllNearExcept(null, pos.getX(), pos.getY(), pos.getZ(), 64, level,
                new BlockTransitionEffectPacket(pos, state, this.target.side(),
                        BlockTransitionEffectPacket.SoundMode.NONE));
        return Result.PICKED_UP;
    }
}
