package cn.ae2bc.platform;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** Forge 1.20 counterpart of the newer block capability cache API. */
public final class ForgeBlockCapabilityCache<T> {
    private final Capability<T> capability;
    private final ServerLevel level;
    private final BlockPos pos;
    @Nullable
    private final Direction context;
    private final BooleanSupplier valid;
    private final Runnable invalidationListener;
    @Nullable
    private LazyOptional<T> cached;

    private ForgeBlockCapabilityCache(Capability<T> capability, ServerLevel level, BlockPos pos,
                                      @Nullable Direction context, BooleanSupplier valid,
                                      Runnable invalidationListener) {
        this.capability = Objects.requireNonNull(capability);
        this.level = Objects.requireNonNull(level);
        this.pos = pos.immutable();
        this.context = context;
        this.valid = Objects.requireNonNull(valid);
        this.invalidationListener = Objects.requireNonNull(invalidationListener);
    }

    public static <T> ForgeBlockCapabilityCache<T> create(Capability<T> capability, ServerLevel level,
                                                           BlockPos pos, @Nullable Direction context) {
        return create(capability, level, pos, context, () -> true, () -> {
        });
    }

    public static <T> ForgeBlockCapabilityCache<T> create(Capability<T> capability, ServerLevel level,
                                                           BlockPos pos, @Nullable Direction context,
                                                           BooleanSupplier valid, Runnable invalidationListener) {
        return new ForgeBlockCapabilityCache<>(capability, level, pos, context, valid, invalidationListener);
    }

    @Nullable
    public T getCapability() {
        if (!valid.getAsBoolean() || !level.hasChunkAt(pos)) {
            return null;
        }
        if (cached == null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return null;
            }
            LazyOptional<T> resolved = blockEntity.getCapability(capability, context);
            resolved.addListener(ignored -> {
                if (cached == resolved) {
                    cached = null;
                    invalidationListener.run();
                }
            });
            cached = resolved;
        }
        return cached.orElse(null);
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public Direction context() {
        return context;
    }
}
