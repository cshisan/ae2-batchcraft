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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
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
            List<ItemStack> expectedDrops = getBlockDrops(level, target);
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
            if (!breakBlockAndHandleDrops(port, level, target, manager, expectedDrops)) {
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

    private List<ItemStack> getBlockDrops(WorldServer level, BlockPos target) {
        ItemStack harvestTool = createHarvestTool(level, target);
        if (!harvestTool.isItemEnchanted()) {
            return Arrays.asList(Platform.getBlockDrops(level, target));
        }

        IBlockState state = level.getBlockState(target);
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, harvestTool);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, harvestTool) > 0
                && state.getBlock().canSilkHarvest(level, target, state, player)) {
            Item item = Item.getItemFromBlock(state.getBlock());
            if (item != Items.AIR) {
                return java.util.Collections.singletonList(
                        new ItemStack(item, 1, state.getBlock().getMetaFromState(state)));
            }
        }
        return state.getBlock().getDrops(level, target, state, fortune);
    }

    private ItemStack createHarvestTool(WorldServer level, BlockPos target) {
        IBlockState state = level.getBlockState(target);
        String harvestTool = state.getBlock().getHarvestTool(state);
        ItemStack tool;
        if ("axe".equals(harvestTool)) {
            tool = new ItemStack(Items.DIAMOND_AXE);
        } else if ("shovel".equals(harvestTool)) {
            tool = new ItemStack(Items.DIAMOND_SHOVEL);
        } else if ("hoe".equals(harvestTool)) {
            tool = new ItemStack(Items.DIAMOND_HOE);
        } else {
            tool = new ItemStack(Items.DIAMOND_PICKAXE);
        }
        EnchantmentHelper.setEnchantments(port.getBreakEnchantments(), tool);
        return tool;
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
                                                    PatternP2PUnitManagerPart manager,
                                                    List<ItemStack> drops) {
        if (!level.destroyBlock(target, false)) return false;
        for (ItemStack drop : drops) {
            ItemStack remainder = manager.isBreakRecovery() ? manager.returnProduct(drop, false) : drop;
            if (!remainder.isEmpty()) {
                level.spawnEntity(new net.minecraft.entity.item.EntityItem(level,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, remainder));
            }
        }
        return true;
    }
}
