package cn.ae2bc.part;

import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.implementations.IPowerChannelState;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.networking.EnergyAcceptorPart;
import cn.ae2bc.logic.FairEnergyDistributor;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.PrioritizedEnergyDistributor;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.core.energy.EnergyEndpoint;
import appeng.me.GridAccessException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.network.NetworkHooks;

/** AE2 8 energy entry point that distributes received power to Pattern P2P outputs. */
public final class PatternP2PTunnelEnergyPart extends EnergyAcceptorPart
        implements IGridTickable, IPowerChannelState {
    public static final int PULL_INTERVAL = 5;
    public static final ResourceLocation MODEL_ID = new ResourceLocation(
            "ae2_batchcraft", "part/p2p/pattern_p2p_tunnel_energy");
    private static final IPartModel MODEL = new PartModel(true, MODEL_ID);

    private boolean transferring;
    private int distributionCursor;
    private long demandCacheTick = Long.MIN_VALUE;
    private int demandCacheFe;
    private long outputCacheTick = Long.MIN_VALUE;
    private List<EnergyEndpoint> outputCache = new ArrayList<EnergyEndpoint>();
    private boolean pullEnabled;
    private int pendingFe;
    private EnergyDistributionMode distributionMode = EnergyDistributionMode.EVEN;
    private boolean clientPowered;
    private boolean clientActive;

    public PatternP2PTunnelEnergyPart(ItemStack stack) {
        super(stack);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return Arrays.asList(MODEL);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }

    public boolean isPullEnabled() { return pullEnabled; }
    public EnergyDistributionMode getDistributionMode() { return distributionMode; }
    @Override public boolean isPowered() {
        return isRemote() ? clientPowered : getProxy().isPowered();
    }
    @Override public boolean isActive() {
        return isRemote() ? clientActive : getGridNode() != null && getGridNode().isActive();
    }

    public void setSettings(boolean pullEnabled, EnergyDistributionMode mode) {
        this.pullEnabled = pullEnabled;
        this.distributionMode = mode == null ? EnergyDistributionMode.EVEN : mode;
        demandCacheTick = Long.MIN_VALUE;
        getHost().markForSave();
        getHost().markForUpdate();
        try {
            if (pullEnabled) getProxy().getTick().wakeDevice(getGridNode());
            else getProxy().getTick().sleepDevice(getGridNode());
        } catch (GridAccessException ignored) {
        }
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        PatternP2PTopologyGridService.invalidate(getGridNode());
        refreshPowerState();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChanged(MENetworkPowerStatusChange event) {
        PatternP2PTopologyGridService.invalidate(getGridNode());
        refreshPowerState();
    }

    private void refreshPowerState() {
        if (isRemote()) {
            return;
        }
        boolean powered = getProxy().isPowered();
        boolean active = getGridNode() != null && getGridNode().isActive();
        if (clientPowered == powered && clientActive == active) {
            return;
        }
        clientPowered = powered;
        clientActive = active;
        getHost().markForUpdate();
    }

    @Override
    public boolean onPartActivate(PlayerEntity player, Hand hand, Vector3d hitPos) {
        if (hand == Hand.MAIN_HAND && player.getItemInHand(hand).isEmpty()) {
            if (!player.level.isClientSide && player instanceof ServerPlayerEntity) {
                NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider(
                        (id, inventory, ignored) -> new PatternP2PTunnelEnergyMenu(id, inventory, this),
                        new TranslationTextComponent("item.ae2_batchcraft.pattern_p2p_tunnel_energy")), buffer -> {
                            buffer.writeBlockPos(getTile().getBlockPos());
                            buffer.writeByte(getSide().getFacing().ordinal());
                            buffer.writeBoolean(pullEnabled);
                            buffer.writeByte(distributionMode.getId());
                        });
            }
            return true;
        }
        return super.onPartActivate(player, hand, hitPos);
    }

    @Override
    public void readFromNBT(CompoundNBT data) {
        super.readFromNBT(data);
        pullEnabled = data.getBoolean("Ae2bcPullEnabled");
        pendingFe = Math.max(0, data.getInt("Ae2bcPendingFe"));
        distributionMode = EnergyDistributionMode.fromId(data.getInt("Ae2bcEnergyDistributionMode"));
    }

    @Override
    public void writeToNBT(CompoundNBT data) {
        super.writeToNBT(data);
        data.putBoolean("Ae2bcPullEnabled", pullEnabled);
        data.putInt("Ae2bcPendingFe", pendingFe);
        data.putInt("Ae2bcEnergyDistributionMode", distributionMode.getId());
    }

    @Override
    public void writeToStream(PacketBuffer data) throws java.io.IOException {
        super.writeToStream(data);
        clientPowered = getProxy().isPowered();
        clientActive = getGridNode() != null && getGridNode().isActive();
        data.writeBoolean(clientPowered);
        data.writeBoolean(clientActive);
    }

    @Override
    public boolean readFromStream(PacketBuffer data) throws java.io.IOException {
        boolean changed = super.readFromStream(data);
        boolean oldPowered = clientPowered;
        boolean oldActive = clientActive;
        clientPowered = data.readBoolean();
        clientActive = data.readBoolean();
        return changed || oldPowered != clientPowered || oldActive != clientActive;
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, PULL_INTERVAL, !pullEnabled, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!pullEnabled || !node.isActive()) return TickRateModulation.SLEEP;
        return pullFromAdjacent() > 0 ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(5, 5, 12, 11, 11, 13);
        helper.addBox(3, 3, 13, 13, 13, 14);
        helper.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    protected double getFunnelPowerDemand(double maxRequired) {
        if (maxRequired <= 0 || transferring) {
            return 0;
        }
        double local = super.getFunnelPowerDemand(maxRequired);
        int remoteLimit = aeToFeFloor(Math.max(0, maxRequired - local));
        int remote = Math.min(remoteLimit, getCachedDemandFe());
        return Math.min(maxRequired, local + PowerUnits.RF.convertTo(PowerUnits.AE, remote));
    }

    @Override
    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        if (power <= 0 || transferring) {
            return power;
        }
        transferring = true;
        try {
            double remaining = super.funnelPowerIntoStorage(power, mode);
            int availableFe = aeToFeFloor(remaining);
            if (availableFe <= 0) {
                return remaining;
            }
            int accepted = distribute(availableFe, mode == Actionable.SIMULATE);
            if (mode == Actionable.MODULATE && accepted > 0) {
                demandCacheTick = Long.MIN_VALUE;
            }
            return Math.max(0, remaining - PowerUnits.RF.convertTo(PowerUnits.AE, accepted));
        } finally {
            transferring = false;
        }
    }

    private int getCachedDemandFe() {
        if (getTile().getLevel() == null) {
            return 0;
        }
        long tick = getTile().getLevel().getGameTime();
        if (demandCacheTick != tick) {
            int total = 0;
            for (EnergyEndpoint output : outputs()) {
                int demand = output.receiveExternalEnergy(Integer.MAX_VALUE, true);
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
        final List<EnergyEndpoint> outputs = outputs();
        if (outputs.isEmpty()) {
            return 0;
        }
        int start = Math.floorMod(distributionCursor, outputs.size());
        int accepted;
        if (distributionMode == EnergyDistributionMode.ROUND_ROBIN) {
            accepted = PrioritizedEnergyDistributor.distribute(offered, outputs.size(),
                    index -> outputs.get(index).receiveExternalEnergy(Integer.MAX_VALUE, true),
                    (index, amount) -> outputs.get(index).receiveExternalEnergy(amount, simulate));
        } else {
            accepted = FairEnergyDistributor.distribute(offered, outputs.size(), start,
                    (index, amount) -> outputs.get(index).receiveExternalEnergy(amount, simulate));
        }
        if (!simulate) {
            distributionCursor = (start + 1) % outputs.size();
        }
        return accepted;
    }

    private List<EnergyEndpoint> outputs() {
        long tick = getTile().getLevel() == null ? Long.MIN_VALUE : getTile().getLevel().getGameTime();
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
        int demand = getCachedDemandFe();
        if (demand <= 0) return accepted;
        Direction face = getSide().getFacing();
        TileEntity tile = getTile().getLevel().getBlockEntity(getTile().getBlockPos().relative(face));
        if (tile == null || isEnergyTunnel(tile, face.getOpposite())) return accepted;
        IEnergyStorage source = tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite()).orElse(null);
        if (source == null || !source.canExtract()) return accepted;
        int available = source.extractEnergy(demand, true);
        int receivable = distribute(available, true);
        if (receivable <= 0) return accepted;
        int extracted = source.extractEnergy(receivable, false);
        int moved = distribute(extracted, false);
        int rejected = extracted - moved;
        if (rejected > 0 && source.canReceive()) rejected -= source.receiveEnergy(rejected, false);
        if (rejected > 0) {
            pendingFe = (int) Math.min(Integer.MAX_VALUE, (long) pendingFe + rejected);
            getHost().markForSave();
        }
        return accepted + moved;
    }

    private int flushPending() {
        if (pendingFe <= 0) return 0;
        int accepted = distribute(pendingFe, false);
        if (accepted > 0) {
            pendingFe -= accepted;
            getHost().markForSave();
        }
        return accepted;
    }

    private static boolean isEnergyTunnel(TileEntity tile, Direction side) {
        if (!(tile instanceof IPartHost)) return false;
        IPart part = ((IPartHost) tile).getPart(side);
        return part instanceof PatternP2PTunnelEnergyPart;
    }

    private static int aeToFeFloor(double amount) {
        double converted = PowerUnits.AE.convertTo(PowerUnits.RF, Math.max(0, amount));
        return converted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) converted;
    }
}
