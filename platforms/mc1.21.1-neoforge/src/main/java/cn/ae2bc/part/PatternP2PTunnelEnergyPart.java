package cn.ae2bc.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartHelper;
import appeng.api.parts.PartModels;
import appeng.api.util.AECableType;
import appeng.parts.networking.EnergyAcceptorPart;
import appeng.parts.p2p.P2PModels;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.logic.PatternP2PEnergyGridService;
import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class PatternP2PTunnelEnergyPart extends EnergyAcceptorPart {
    public static final int PULL_INTERVAL = 5;
    private static final String PENDING_FE_TAG = "PendingFe";
    private static final P2PModels MODELS = new P2PModels(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/p2p/pattern_p2p_tunnel_energy"));

    private final IEnergyStorage energyStorage = new DynamicEnergyStorage();
    private boolean transferringEnergy;
    private boolean pullEnabled;
    private int pullInterval = PULL_INTERVAL;
    private long demandCacheTick = Long.MIN_VALUE;
    private int demandCacheFe;
    private int pendingFe;
    private BlockCapabilityCache<IEnergyStorage, Direction> sourceEnergyCache;
    private boolean sourceIsEnergyTunnel;
    private boolean sourceResolved;

    public PatternP2PTunnelEnergyPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IGridTickable.class, new PullTicker());
    }

    @Override
    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public boolean isPullEnabled() {
        return pullEnabled;
    }

    public int getPullInterval() {
        return pullInterval;
    }

    public void setPullEnabled(boolean enabled) {
        if (pullEnabled == enabled) {
            return;
        }
        pullEnabled = enabled;
        pullInterval = PULL_INTERVAL;
        getHost().markForSave();
        getHost().markForUpdate();
        var grid = getMainNode().getGrid();
        if (grid != null) {
            if (enabled) {
                grid.getTickManager().wakeDevice(getMainNode().getNode());
            } else {
                grid.getTickManager().sleepDevice(getMainNode().getNode());
            }
        }
    }

    public static void registerModels() {
        PartModels.registerModels(MODELS.getModels().stream()
                .flatMap(model -> model.getModels().stream())
                .toList());
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        pullEnabled = data.getBoolean("PullEnabled");
        pendingFe = Math.max(0, data.getInt(PENDING_FE_TAG));
        pullInterval = PULL_INTERVAL;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putBoolean("PullEnabled", pullEnabled);
        if (pendingFe > 0) {
            data.putInt(PENDING_FE_TAG, pendingFe);
        } else {
            data.remove(PENDING_FE_TAG);
        }
    }

    @Override
    protected double getFunnelPowerDemand(double maxRequired) {
        if (maxRequired <= 0) {
            return 0;
        }

        double localDemand = super.getFunnelPowerDemand(maxRequired);
        double remainingCapacity = Math.max(0, maxRequired - localDemand);
        int remoteLimit = aeToFeFloor(remainingCapacity);
        var energyService = getEnergyGridService();
        int remoteDemand = energyService == null ? 0 : energyService.getDemand(remoteLimit);
        return Math.min(maxRequired, localDemand + PowerUnit.FE.convertTo(PowerUnit.AE, remoteDemand));
    }

    @Override
    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        if (power <= 0 || transferringEnergy) {
            return power;
        }

        transferringEnergy = true;
        try {
            double remaining = super.funnelPowerIntoStorage(power, mode);
            int availableFe = aeToFeFloor(remaining);
            if (availableFe <= 0) {
                return remaining;
            }

            int acceptedFe = distributeToOutputs(availableFe, mode == Actionable.SIMULATE);
            double acceptedAe = PowerUnit.FE.convertTo(PowerUnit.AE, acceptedFe);
            return Math.max(0, remaining - acceptedAe);
        } finally {
            transferringEnergy = false;
        }
    }

    private int distributeToOutputs(int offered, boolean simulate) {
        if (offered <= 0) {
            return 0;
        }
        var energyService = getEnergyGridService();
        return energyService == null ? 0 : energyService.distribute(offered, simulate);
    }

    private int getPullDemandFe() {
        double maxAe = PowerUnit.FE.convertTo(PowerUnit.AE, Integer.MAX_VALUE);
        return aeToFeCeil(getFunnelPowerDemand(maxAe));
    }

    private int getCachedDemandFe() {
        var blockEntity = getBlockEntity();
        var level = blockEntity == null ? null : blockEntity.getLevel();
        if (level == null) {
            return 0;
        }

        long gameTime = level.getGameTime();
        if (demandCacheTick != gameTime) {
            demandCacheFe = getPullDemandFe();
            demandCacheTick = gameTime;
        }
        return demandCacheFe;
    }

    private void invalidateDemandCache() {
        demandCacheTick = Long.MIN_VALUE;
    }

    private PullOutcome pullEnergyFromAdjacent() {
        int flushed = flushPendingEnergy();
        if (pendingFe > 0) {
            return new PullOutcome(flushed, getPullDemandFe(), true);
        }
        int demand = getPullDemandFe();
        if (demand <= 0) {
            return PullOutcome.NO_DEMAND;
        }

        Direction side = getSide();
        if (side == null || !(getLevel() instanceof ServerLevel level)) {
            return PullOutcome.NO_SOURCE;
        }

        var targetPos = getBlockEntity().getBlockPos().relative(side);
        Direction targetSide = side.getOpposite();
        if (!sourceResolved) {
            var targetHost = PartHelper.getPartHost(level, targetPos);
            sourceIsEnergyTunnel = targetHost != null
                    && targetHost.getPart(targetSide) instanceof PatternP2PTunnelEnergyPart;
            sourceResolved = true;
        }
        if (sourceIsEnergyTunnel) {
            return PullOutcome.NO_SOURCE;
        }

        if (sourceEnergyCache == null
                || sourceEnergyCache.level() != level
                || !sourceEnergyCache.pos().equals(targetPos)
                || sourceEnergyCache.context() != targetSide) {
            sourceEnergyCache = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK, level, targetPos, targetSide);
        }
        var source = sourceEnergyCache.getCapability();
        if (source == null || !source.canExtract()) {
            return PullOutcome.NO_SOURCE;
        }

        int available = source.extractEnergy(demand, true);
        int receivable = getEnergyStorage().receiveEnergy(available, true);
        if (receivable <= 0) {
            return PullOutcome.NO_SOURCE;
        }

        int extracted = source.extractEnergy(receivable, false);
        int accepted = getEnergyStorage().receiveEnergy(extracted, false);
        int rejected = extracted - accepted;
        if (rejected > 0 && source.canReceive()) {
            rejected -= source.receiveEnergy(rejected, false);
        }
        if (rejected > 0) {
            pendingFe = (int) Math.min(Integer.MAX_VALUE, (long) pendingFe + rejected);
            getHost().markForSave();
        }

        int remainingDemand = getPullDemandFe();
        boolean sourceHasMore = remainingDemand > 0
                && source.extractEnergy(remainingDemand, true) > 0;
        return new PullOutcome(accepted, remainingDemand, sourceHasMore);
    }

    private int flushPendingEnergy() {
        if (pendingFe <= 0 || transferringEnergy) {
            return 0;
        }
        transferringEnergy = true;
        try {
            int accepted = distributeToOutputs(pendingFe, false);
            pendingFe -= accepted;
            if (accepted > 0) {
                getHost().markForSave();
            }
            return accepted;
        } finally {
            transferringEnergy = false;
        }
    }

    private PatternP2PEnergyGridService getEnergyGridService() {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return grid.getService(PatternP2PEnergyGridService.class);
    }

    private static int aeToFeFloor(double amount) {
        double converted = PowerUnit.AE.convertTo(PowerUnit.FE, Math.max(0, amount));
        if (converted >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) converted;
    }

    private static int aeToFeCeil(double amount) {
        double converted = PowerUnit.AE.convertTo(PowerUnit.FE, Math.max(0, amount));
        if (converted >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(converted);
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(5, 5, 12, 11, 11, 13);
        helper.addBox(3, 3, 13, 13, 13, 14);
        helper.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            openConfigurationMenu(player);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (hand == InteractionHand.MAIN_HAND && heldItem.isEmpty()) {
            if (!isClientSide()) {
                openConfigurationMenu(player);
            }
            return true;
        }
        return super.onUseItemOn(heldItem, player, hand, pos);
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.level.BlockGetter level,
                                  net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos neighbor) {
        sourceEnergyCache = null;
        sourceIsEnergyTunnel = false;
        sourceResolved = false;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getTickManager().alertDevice(getMainNode().getNode());
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        invalidateDemandCache();
        if (pullEnabled) {
            var grid = getMainNode().getGrid();
            if (grid != null) {
                grid.getTickManager().alertDevice(getMainNode().getNode());
            }
        }
    }

    private void openConfigurationMenu(Player player) {
        MenuOpener.open(PatternP2PTunnelEnergyMenu.TYPE, player, MenuLocators.forPart(this));
    }

    @Override
    protected boolean shouldSendMissingChannelStateToClient() {
        return false;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }

    private final class PullTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, PULL_INTERVAL, !pullEnabled, PULL_INTERVAL);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!pullEnabled || transferringEnergy) {
                pullInterval = PULL_INTERVAL;
                return TickRateModulation.SLEEP;
            }

            var result = pullEnergyFromAdjacent();
            if (result.accepted() > 0) {
                pullInterval = 1;
                return TickRateModulation.URGENT;
            }
            if (result.remainingDemand() <= 0 || !result.sourceHasMore()) {
                pullInterval = PULL_INTERVAL;
                return TickRateModulation.IDLE;
            }

            pullInterval = Math.min(PULL_INTERVAL, pullInterval + 1);
            return TickRateModulation.SLOWER;
        }
    }

    private final class DynamicEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            if (!simulate) {
                flushPendingEnergy();
            }
            double overflow = injectExternalPower(PowerUnit.FE, maxReceive,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE);
            int accepted = (int) Math.clamp(maxReceive - overflow, 0, maxReceive);
            if (!simulate && accepted > 0) {
                invalidateDemandCache();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return pendingFe;
        }

        @Override
        public int getMaxEnergyStored() {
            long capacity = (long) pendingFe + getCachedDemandFe();
            return (int) Math.min(Integer.MAX_VALUE, capacity);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private record PullOutcome(int accepted, int remainingDemand, boolean sourceHasMore) {
        private static final PullOutcome NO_DEMAND = new PullOutcome(0, 0, false);
        private static final PullOutcome NO_SOURCE = new PullOutcome(0, 1, false);

        private boolean shouldAccelerate() {
            return accepted > 0 && remainingDemand > 0 && sourceHasMore;
        }
    }

}
