package cn.ae2bc.part;

import cn.ae2bc.core.ProjectLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.powersink.IExternalPowerSink;
import cn.ae2bc.logic.FairEnergyDistributor;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.PrioritizedEnergyDistributor;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.Ae2bcMod;
import appeng.me.GridAccessException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** rv6 energy entry point with a small platform adapter around the shared distributor. */
public final class PatternP2PTunnelEnergyPart extends PartBasicState
        implements IExternalPowerSink, IGridTickable {
    public static final int PULL_INTERVAL = ProjectLimits.ENERGY_TUNNEL_PULL_INTERVAL;
    public static final ResourceLocation MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_energy");
    private static final IPartModel MODEL = new PartModel(true, MODEL_ID);

    private final IEnergyStorage energyStorage = new DynamicEnergyStorage();
    private boolean transferring;
    private int distributionCursor;
    private final Map<Object, Integer> groupDistributionCursors = new HashMap<Object, Integer>();
    private long demandCacheTick = Long.MIN_VALUE;
    private int demandCacheFe;
    private long outputCacheTick = Long.MIN_VALUE;
    private List<EnergyEndpoint> outputCache = new ArrayList<EnergyEndpoint>();
    private boolean pullEnabled;
    private int pendingFe;
    private EnergyDistributionMode distributionMode = EnergyDistributionMode.EVEN;

    public PatternP2PTunnelEnergyPart(ItemStack stack) {
        super(stack);
        getProxy().setFlags();
    }

    @Override public IPartModel getStaticModels() { return MODEL; }
    public boolean isPullEnabled() { return pullEnabled; }
    public EnergyDistributionMode getDistributionMode() { return distributionMode; }

    public void setSettings(boolean enabled, EnergyDistributionMode mode) {
        pullEnabled = enabled;
        distributionMode = mode == null ? EnergyDistributionMode.EVEN : mode;
        demandCacheTick = Long.MIN_VALUE;
        saveChanges();
        getHost().markForUpdate();
        try {
            if (enabled) getProxy().getTick().wakeDevice(getGridNode());
            else getProxy().getTick().sleepDevice(getGridNode());
        } catch (GridAccessException ignored) {
        }
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        PatternP2PTopologyGridService.invalidate(getGridNode());
        outputCacheTick = Long.MIN_VALUE;
        demandCacheTick = Long.MIN_VALUE;
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d hit) {
        if (hand == EnumHand.MAIN_HAND) {
            if (!player.world.isRemote) {
                player.openGui(Ae2bcMod.INSTANCE, Ae2bcMod.GUI_ENERGY_BASE
                                + getSide().getFacing().ordinal(), player.world,
                        getTile().getPos().getX(), getTile().getPos().getY(), getTile().getPos().getZ());
            }
            return true;
        }
        return super.onPartActivate(player, hand, hit);
    }

    @Override public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        pullEnabled = data.getBoolean("Ae2bcPullEnabled");
        pendingFe = Math.max(0, data.getInteger("Ae2bcPendingFe"));
        distributionMode = EnergyDistributionMode.fromId(data.getInteger("Ae2bcEnergyDistributionMode"));
    }

    @Override public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("Ae2bcPullEnabled", pullEnabled);
        data.setInteger("Ae2bcPendingFe", pendingFe);
        data.setInteger("Ae2bcEnergyDistributionMode", distributionMode.getId());
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, PULL_INTERVAL, !pullEnabled, false);
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!pullEnabled || transferring) return TickRateModulation.SLEEP;
        return pullFromAdjacent() > 0 ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(5, 5, 12, 11, 11, 13);
        helper.addBox(3, 3, 13, 13, 13, 14);
        helper.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public double injectExternalPower(PowerUnits unit, double amount, Actionable mode) {
        double offeredAe = unit.convertTo(PowerUnits.AE, Math.max(0, amount));
        double overflowAe = funnelPowerIntoNetwork(offeredAe, mode);
        return PowerUnits.AE.convertTo(unit, overflowAe);
    }

    @Override
    public double getExternalPowerDemand(PowerUnits unit, double maxPowerRequired) {
        double limitAe = unit.convertTo(PowerUnits.AE, Math.max(0, maxPowerRequired));
        double demandAe = getCombinedPowerDemand(limitAe);
        return PowerUnits.AE.convertTo(unit, demandAe);
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        return amount;
    }

    private double funnelPowerIntoNetwork(double amount, Actionable mode) {
        if (amount <= 0 || transferring) return amount;
        transferring = true;
        try {
            double remaining = injectIntoLocalNetwork(amount, mode);
            int offeredFe = aeToFeFloor(remaining);
            int accepted = distribute(offeredFe, mode == Actionable.SIMULATE);
            if (mode == Actionable.MODULATE && accepted > 0) demandCacheTick = Long.MIN_VALUE;
            return Math.max(0, remaining - PowerUnits.RF.convertTo(PowerUnits.AE, accepted));
        } finally {
            transferring = false;
        }
    }

    @Override public double getAEMaxPower() { return 0; }
    @Override public double getAECurrentPower() { return 0; }
    @Override public boolean isAEPublicPowerStorage() { return false; }
    @Override public AccessRestriction getPowerFlow() { return AccessRestriction.READ_WRITE; }
    @Override public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) { return 0; }

    private double getCombinedPowerDemand(double maxRequired) {
        if (maxRequired <= 0 || transferring) return 0;
        double localDemand = getLocalNetworkDemand(maxRequired);
        double remainingCapacity = Math.max(0, maxRequired - localDemand);
        int remoteLimit = aeToFeFloor(remainingCapacity);
        int remoteDemand = Math.min(remoteLimit, getCachedDemandFe());
        return Math.min(maxRequired,
                localDemand + PowerUnits.RF.convertTo(PowerUnits.AE, remoteDemand));
    }

    private double getLocalNetworkDemand(double maxRequired) {
        try {
            return Math.max(0, getProxy().getEnergy().getEnergyDemand(maxRequired));
        } catch (GridAccessException ignored) {
            return 0;
        }
    }

    private double injectIntoLocalNetwork(double amount, Actionable mode) {
        try {
            return getProxy().getEnergy().injectPower(amount, mode);
        } catch (GridAccessException ignored) {
            return amount;
        }
    }

    @Override public boolean hasCapability(Capability<?> capability) {
        return capability == CapabilityEnergy.ENERGY || super.hasCapability(capability);
    }

    @Override public <T> T getCapability(Capability<T> capability) {
        return capability == CapabilityEnergy.ENERGY
                ? CapabilityEnergy.ENERGY.cast(energyStorage) : super.getCapability(capability);
    }

    private int getCachedDemandFe() {
        if (getTile().getWorld() == null) return 0;
        long tick = getTile().getWorld().getTotalWorldTime();
        if (demandCacheTick != tick) {
            int total = 0;
            for (EnergyGroup group : energyGroups()) {
                int demand = group.demand();
                if (Integer.MAX_VALUE - total < demand) {
                    total = Integer.MAX_VALUE;
                    break;
                }
                total += demand;
            }
            demandCacheFe = total;
            demandCacheTick = tick;
        }
        return demandCacheFe;
    }

    private int distribute(int offered, boolean simulate) {
        final List<EnergyGroup> groups = energyGroups();
        if (groups.isEmpty()) return 0;
        final int[] allocations = new int[groups.size()];
        int start = Math.floorMod(distributionCursor, groups.size());
        if (distributionMode == EnergyDistributionMode.ROUND_ROBIN) {
            PrioritizedEnergyDistributor.distribute(offered, groups.size(),
                    index -> groups.get(Math.floorMod(start + index, groups.size())).demand(),
                    (index, amount) -> reserveAllocation(
                            allocations, Math.floorMod(start + index, groups.size()), amount,
                            groups.get(Math.floorMod(start + index, groups.size())).demand()));
        } else {
            FairEnergyDistributor.distribute(offered, groups.size(), start,
                    (index, amount) -> reserveAllocation(
                            allocations, index, amount, groups.get(index).demand()));
        }
        int accepted = 0;
        for (int index = 0; index < groups.size(); index++) {
            accepted += groups.get(index).receive(allocations[index], simulate);
        }
        if (!simulate) distributionCursor = (start + 1) % groups.size();
        return accepted;
    }

    private static int reserveAllocation(int[] allocations, int index, int amount, int demand) {
        int accepted = Math.min(amount, Math.max(0, demand - allocations[index]));
        allocations[index] += accepted;
        return accepted;
    }

    private List<EnergyGroup> energyGroups() {
        LinkedHashMap<Object, EnergyGroup> grouped = new LinkedHashMap<Object, EnergyGroup>();
        for (EnergyEndpoint endpoint : outputs()) {
            Object key = energyGroupKey(endpoint);
            if (key == null) continue;
            EnergyGroup group = grouped.get(key);
            if (group == null) {
                group = new EnergyGroup(key);
                grouped.put(key, group);
            }
            group.endpoints.add(endpoint);
        }
        groupDistributionCursors.keySet().retainAll(grouped.keySet());
        return new ArrayList<EnergyGroup>(grouped.values());
    }

    private static Object energyGroupKey(EnergyEndpoint endpoint) {
        if (endpoint instanceof PatternP2PTunnelPart) {
            short frequency = ((PatternP2PTunnelPart) endpoint).getFrequency();
            return frequency == 0 ? null : Short.valueOf(frequency);
        }
        if (endpoint instanceof PatternP2PUnitPortPart) {
            UUID managerId = ((PatternP2PUnitPortPart) endpoint).getBoundManagerId();
            return managerId;
        }
        return endpoint;
    }

    private final class EnergyGroup {
        private final Object key;
        private final List<EnergyEndpoint> endpoints = new ArrayList<EnergyEndpoint>();

        private EnergyGroup(Object key) {
            this.key = key;
        }

        private int demand() {
            int total = 0;
            for (EnergyEndpoint endpoint : endpoints) {
                int demand = endpoint.receiveExternalEnergy(Integer.MAX_VALUE, true);
                if (Integer.MAX_VALUE - total < demand) return Integer.MAX_VALUE;
                total += demand;
            }
            return total;
        }

        private int receive(int offered, boolean simulate) {
            if (offered <= 0 || endpoints.isEmpty()) return 0;
            int cursor = groupDistributionCursors.containsKey(key)
                    ? groupDistributionCursors.get(key).intValue() : 0;
            final int start = Math.floorMod(cursor, endpoints.size());
            EnergyDistributionMode mode = groupMode();
            int accepted;
            if (mode == EnergyDistributionMode.ROUND_ROBIN) {
                accepted = PrioritizedEnergyDistributor.distribute(offered, endpoints.size(),
                        index -> endpoints.get(Math.floorMod(start + index, endpoints.size()))
                                .receiveExternalEnergy(Integer.MAX_VALUE, true),
                        (index, amount) -> endpoints.get(Math.floorMod(start + index, endpoints.size()))
                                .receiveExternalEnergy(amount, simulate));
            } else {
                accepted = FairEnergyDistributor.distribute(offered, endpoints.size(), start,
                        (index, amount) -> endpoints.get(index).receiveExternalEnergy(amount, simulate));
            }
            if (!simulate) groupDistributionCursors.put(key, Integer.valueOf((start + 1) % endpoints.size()));
            return accepted;
        }

        private EnergyDistributionMode groupMode() {
            EnergyEndpoint first = endpoints.get(0);
            if (first instanceof PatternP2PUnitPortPart) {
                PatternP2PUnitManagerPart manager = ((PatternP2PUnitPortPart) first).findManager();
                return manager == null ? EnergyDistributionMode.EVEN : manager.getEnergyDistributionMode();
            }
            return distributionMode;
        }
    }

    private List<EnergyEndpoint> outputs() {
        long tick = getTile().getWorld() == null ? Long.MIN_VALUE
                : getTile().getWorld().getTotalWorldTime();
        if (outputCacheTick == tick) return outputCache;
        IGridNode ownNode = getGridNode();
        List<EnergyEndpoint> result = PatternP2PTopologyGridService.findEnergyEndpoints(ownNode);
        outputCache = result;
        outputCacheTick = tick;
        return outputCache;
    }

    private int pullFromAdjacent() {
        int accepted = flushPending();
        if (pendingFe > 0) return accepted;
        int demand = getPullDemandFe();
        if (demand <= 0) return accepted;
        EnumFacing face = getSide().getFacing();
        TileEntity tile = getTile().getWorld().getTileEntity(getTile().getPos().offset(face));
        if (tile == null || isEnergyTunnel(tile, face.getOpposite())) return accepted;
        IEnergyStorage source = tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
        if (source == null || !source.canExtract()) return accepted;
        int available = source.extractEnergy(demand, true);
        int receivable = energyStorage.receiveEnergy(available, true);
        if (receivable <= 0) return accepted;
        int extracted = source.extractEnergy(receivable, false);
        int moved = energyStorage.receiveEnergy(extracted, false);
        int rejected = extracted - moved;
        if (rejected > 0 && source.canReceive()) rejected -= source.receiveEnergy(rejected, false);
        if (rejected > 0) {
            pendingFe = (int) Math.min(Integer.MAX_VALUE, (long) pendingFe + rejected);
            saveChanges();
        }
        return accepted + moved;
    }

    private int flushPending() {
        if (pendingFe <= 0) return 0;
        int accepted = energyStorage.receiveEnergy(pendingFe, false);
        if (accepted > 0) {
            pendingFe -= accepted;
            saveChanges();
        }
        return accepted;
    }

    private static boolean isEnergyTunnel(TileEntity tile, EnumFacing side) {
        if (!(tile instanceof IPartHost)) return false;
        IPart part = ((IPartHost) tile).getPart(side);
        return part instanceof PatternP2PTunnelEnergyPart;
    }

    private static int aeToFeFloor(double amount) {
        double converted = PowerUnits.AE.convertTo(PowerUnits.RF, Math.max(0, amount));
        return converted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) converted;
    }

    private static int aeToFeCeil(double amount) {
        double converted = PowerUnits.AE.convertTo(PowerUnits.RF, Math.max(0, amount));
        return converted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(converted);
    }

    private int getPullDemandFe() {
        double maxAe = PowerUnits.RF.convertTo(PowerUnits.AE, Integer.MAX_VALUE);
        return aeToFeCeil(getCombinedPowerDemand(maxAe));
    }

    private final class DynamicEnergyStorage implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            double overflow = injectExternalPower(PowerUnits.RF, maxReceive,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE);
            return Math.max(0, Math.min(maxReceive, maxReceive - (int) Math.ceil(overflow)));
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return getPullDemandFe(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
