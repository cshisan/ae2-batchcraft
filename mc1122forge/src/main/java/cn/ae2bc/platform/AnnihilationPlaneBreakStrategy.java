package cn.ae2bc.platform;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.core.AppEng;
import appeng.core.sync.packets.PacketTransitionEffect;
import appeng.hooks.TickHandler;
import appeng.me.GridAccessException;
import appeng.util.IWorldCallable;
import appeng.util.Platform;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Arrays;
import java.util.List;

/** Applies the rv6 annihilation-plane checks, power use and deferred world mutation. */
public final class AnnihilationPlaneBreakStrategy
        implements IWorldCallable<TickRateModulation> {
    private final PatternP2PUnitPortPart port;
    private boolean breaking;

    public AnnihilationPlaneBreakStrategy(PatternP2PUnitPortPart port) {
        this.port = port;
    }

    public TickRateModulation tick(PatternP2PUnitManagerPart manager) {
        if (breaking) return TickRateModulation.URGENT;
        return tryBreak(manager, false);
    }

    public void reset() {
        breaking = false;
    }

    @Override
    public TickRateModulation call(World world) {
        if (!breaking) return TickRateModulation.IDLE;
        breaking = false;
        PatternP2PUnitManagerPart manager = port.findManager();
        if (manager == null || !manager.isTaskActive()) return TickRateModulation.IDLE;
        return tryBreak(manager, true);
    }

    private TickRateModulation tryBreak(PatternP2PUnitManagerPart manager, boolean modulate) {
        if (!(port.getTile().getWorld() instanceof WorldServer) || port.getSide() == null) {
            return TickRateModulation.IDLE;
        }
        WorldServer level = (WorldServer) port.getTile().getWorld();
        BlockPos target = port.getTile().getPos().offset(port.getSide().getFacing());
        try {
            if (!canHandleBlock(level, target)) return TickRateModulation.IDLE;
            List<ItemStack> expectedDrops = Arrays.asList(Platform.getBlockDrops(level, target));
            if (!canReturnDrops(port, manager, expectedDrops)) return TickRateModulation.IDLE;

            float energyUsage = calculateEnergyUsage(level, target, expectedDrops);
            IEnergyGrid energy = port.getProxy().getEnergy();
            boolean hasPower = energy.extractAEPower(energyUsage, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) > energyUsage - 0.1;
            if (!hasPower) return TickRateModulation.IDLE;

            if (!modulate) {
                breaking = true;
                TickHandler.INSTANCE.addCallable(level, this);
                return TickRateModulation.URGENT;
            }

            energy.extractAEPower(energyUsage, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!breakBlockAndHandleDrops(port, level, target, manager)) {
                return TickRateModulation.IDLE;
            }
            AppEng.proxy.sendToAllNearExcept(null, target.getX(), target.getY(), target.getZ(),
                    64.0, level, new PacketTransitionEffect(target.getX(), target.getY(),
                            target.getZ(), port.getSide(), true));
            return TickRateModulation.URGENT;
        } catch (GridAccessException ignored) {
            return TickRateModulation.IDLE;
        }
    }

    private static boolean canHandleBlock(WorldServer level, BlockPos target) {
        IBlockState state = level.getBlockState(target);
        Material material = state.getMaterial();
        Block block = state.getBlock();
        boolean invalidMaterial = material == Material.AIR || material == Material.LAVA
                || material == Material.WATER || material.isLiquid();
        boolean blacklisted = block == Blocks.BEDROCK || block == Blocks.END_PORTAL
                || block == Blocks.END_PORTAL_FRAME || block == Blocks.COMMAND_BLOCK;
        return !invalidMaterial && !blacklisted && state.getBlockHardness(level, target) >= 0
                && !level.isAirBlock(target) && level.isBlockLoaded(target)
                && level.canMineBlockBody(Platform.getPlayer(level), target);
    }

    private static boolean canReturnDrops(PatternP2PUnitPortPart port,
                                          PatternP2PUnitManagerPart manager,
                                          List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && (!port.allowsInputFilter(drop)
                    || !manager.returnProduct(drop, true).isEmpty())) return false;
        }
        return true;
    }

    private static float calculateEnergyUsage(WorldServer level, BlockPos target,
                                              List<ItemStack> drops) {
        float energyUsage = 1.0F + level.getBlockState(target).getBlockHardness(level, target);
        for (ItemStack drop : drops) energyUsage += drop.getCount();
        return energyUsage;
    }

    private static boolean breakBlockAndHandleDrops(PatternP2PUnitPortPart port,
                                                    WorldServer level, BlockPos target,
                                                    PatternP2PUnitManagerPart manager) {
        if (!level.destroyBlock(target, true)) return false;
        if (!manager.isBreakRecovery()) return true;
        AxisAlignedBB area = new AxisAlignedBB(target).grow(0.2);
        for (EntityItem entity : level.getEntitiesWithinAABB(EntityItem.class, area)) {
            ItemStack offered = entity.getItem().copy();
            if (offered.isEmpty() || !port.allowsInputFilter(offered)) continue;
            ItemStack remainder = manager.returnProduct(offered, false);
            if (remainder.isEmpty()) entity.setDead(); else entity.setItem(remainder);
        }
        return true;
    }
}
