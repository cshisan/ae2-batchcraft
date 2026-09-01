package cn.ae2bc.logic;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Grid-wide receiver registry and one-tick demand snapshot for Pattern P2P energy. */
public final class PatternP2PEnergyGridService implements IGridService, IGridServiceProvider {
    private static final String JOIN_ORDER_TAG = "ae2bcEnergyJoinOrder";
    private static final AtomicLong NEXT_JOIN_ORDER = new AtomicLong();

    private final IGrid grid;
    private final List<SinkEntry> sinks = new ArrayList<>();
    private final IdentityHashMap<IGridNode, SinkEntry> sinksByNode = new IdentityHashMap<>();
    private final IdentityHashMap<IGridNode, Long> ordersByNode = new IdentityHashMap<>();
    private final IdentityHashMap<IGridNode, Boolean> energyTunnels = new IdentityHashMap<>();
    private final Map<UUID, PatternP2PUnitManagerPart> managers = new HashMap<>();
    private final List<ReceiverGroup> groups = new ArrayList<>();

    private boolean topologyDirty = true;
    private long tickEpoch;
    private long snapshotEpoch = Long.MIN_VALUE;
    private long totalDemand;
    private int distributionCursor;
    private EnergyDistributionMode globalEnergyDistributionMode = EnergyDistributionMode.EVEN;
    private boolean globalEnergyDistributionModeInitialized;

    public PatternP2PEnergyGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void onServerStartTick() {
        tickEpoch++;
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        Object owner = node.getOwner();
        if (owner instanceof PatternP2PTunnelEnergyPart) {
            energyTunnels.put(node, Boolean.TRUE);
        }
        if (owner instanceof PatternP2PUnitManagerPart manager) {
            managers.put(manager.getPatternP2PUnitId(), manager);
            boolean modeChanged = initializeOrApplyGlobalMode(manager);
            invalidateSnapshot();
            if (modeChanged) {
                demandChanged();
            }
        }

        if (!(owner instanceof PatternP2PTunnelPart output && output.isStandardOutput())
                && !(owner instanceof PatternP2PUnitPortPart port && port.getType().acceptsExternalEnergy())) {
            return;
        }

        long order = readOrCreateJoinOrder(savedData);
        var entry = new SinkEntry(node, owner, order);
        sinksByNode.put(node, entry);
        ordersByNode.put(node, order);
        int insertionPoint = java.util.Collections.binarySearch(sinks, entry,
                Comparator.comparingLong(SinkEntry::joinOrder));
        sinks.add(insertionPoint < 0 ? -insertionPoint - 1 : insertionPoint, entry);
        topologyDirty = true;
        invalidateSnapshot();

        if (owner instanceof PatternP2PTunnelPart output) {
            initializeOrApplyGlobalMode(output);
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        Object owner = node.getOwner();
        energyTunnels.remove(node);
        if (owner instanceof PatternP2PUnitManagerPart manager) {
            managers.remove(manager.getPatternP2PUnitId(), manager);
        }
        SinkEntry removed = sinksByNode.remove(node);
        ordersByNode.remove(node);
        if (removed != null) {
            sinks.remove(removed);
            topologyDirty = true;
        }
        invalidateSnapshot();
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        Long order = ordersByNode.get(node);
        if (order != null) {
            savedData.putLong(JOIN_ORDER_TAG, order);
        }
    }

    public int getDemand(int limit) {
        if (limit <= 0) {
            return 0;
        }
        refreshDemandSnapshot();
        return (int) Math.min(limit, totalDemand);
    }

    public int distribute(int offered, boolean simulate) {
        if (offered <= 0) {
            return 0;
        }
        refreshDemandSnapshot();
        int allocatable = (int) Math.min(offered, totalDemand);
        if (allocatable <= 0 || simulate) {
            return allocatable;
        }

        for (var group : groups) {
            group.allocation = 0;
        }
        for (var sink : sinks) {
            sink.allocation = 0;
        }

        int sinkCount = sinks.size();
        int startIndex = sinkCount == 0 ? 0 : Math.floorMod(distributionCursor, sinkCount);
        FairEnergyDistributor.distribute(allocatable, sinkCount, startIndex, (index, amount) -> {
            SinkEntry sink = sinks.get(index);
            int accepted = Math.min(amount, sink.remainingDemand());
            sink.allocation += accepted;
            return accepted;
        });
        if (sinkCount > 0) {
            distributionCursor = (startIndex + 1) % sinkCount;
        }

        for (var sink : sinks) {
            if (sink.group != null) {
                sink.group.allocation += sink.allocation;
            }
        }

        int accepted = 0;
        for (var group : groups) {
            accepted += group.distribute();
        }
        totalDemand = Math.max(0, totalDemand - accepted);
        return accepted;
    }

    public @Nullable PatternP2PUnitManagerPart findManager(UUID id) {
        return id == null ? null : managers.get(id);
    }

    public void topologyChanged() {
        topologyDirty = true;
        demandChanged();
    }

    public void demandChanged() {
        invalidateSnapshot();
        for (var node : energyTunnels.keySet()) {
            if (node.getGrid() == grid) {
                grid.getTickManager().alertDevice(node);
            }
        }
    }

    public EnergyDistributionMode getGlobalEnergyDistributionMode() {
        return globalEnergyDistributionMode;
    }

    public void setGlobalEnergyDistributionMode(EnergyDistributionMode mode) {
        if (mode == null || (globalEnergyDistributionModeInitialized
                && mode == globalEnergyDistributionMode)) {
            return;
        }
        globalEnergyDistributionMode = mode;
        globalEnergyDistributionModeInitialized = true;
        for (var sink : sinks) {
            if (sink.owner instanceof PatternP2PTunnelPart output) {
                output.getOutputLogic().applyEnergyDistributionMode(mode);
            }
        }
        for (var manager : managers.values()) {
            manager.getLogic().applyEnergyDistributionMode(mode);
        }
        demandChanged();
    }

    private void initializeOrApplyGlobalMode(PatternP2PTunnelPart output) {
        if (!globalEnergyDistributionModeInitialized) {
            globalEnergyDistributionMode = output.getOutputLogic().getEnergyDistributionMode();
            globalEnergyDistributionModeInitialized = true;
        } else {
            output.getOutputLogic().applyEnergyDistributionMode(globalEnergyDistributionMode);
        }
    }

    private boolean initializeOrApplyGlobalMode(PatternP2PUnitManagerPart manager) {
        if (!globalEnergyDistributionModeInitialized) {
            globalEnergyDistributionMode = manager.getLogic().getEnergyDistributionMode();
            globalEnergyDistributionModeInitialized = true;
            return false;
        } else {
            return manager.getLogic().applyEnergyDistributionMode(globalEnergyDistributionMode);
        }
    }

    public void synchronizeOutputGroupMode(PatternP2PTunnelPart origin, EnergyDistributionMode requestedMode) {
        if (origin == null || !origin.isStandardOutput() || origin.getFrequency() == 0) {
            return;
        }
        EnergyDistributionMode mode = requestedMode;
        if (mode == null) {
            for (var sink : sinks) {
                if (sink.owner instanceof PatternP2PTunnelPart candidate
                        && candidate.isStandardOutput() && candidate.getFrequency() == origin.getFrequency()) {
                    mode = candidate.getOutputLogic().getEnergyDistributionMode();
                    break;
                }
            }
        }
        if (mode == null) {
            mode = EnergyDistributionMode.EVEN;
        }
        for (var sink : sinks) {
            if (sink.owner instanceof PatternP2PTunnelPart candidate
                    && candidate.isStandardOutput() && candidate.getFrequency() == origin.getFrequency()) {
                candidate.getOutputLogic().applyEnergyDistributionMode(mode);
            }
        }
        demandChanged();
    }

    private long readOrCreateJoinOrder(@Nullable CompoundTag savedData) {
        if (savedData != null && savedData.contains(JOIN_ORDER_TAG)) {
            long saved = savedData.getLong(JOIN_ORDER_TAG);
            NEXT_JOIN_ORDER.accumulateAndGet(saved, Math::max);
            return saved;
        }
        return NEXT_JOIN_ORDER.incrementAndGet();
    }

    private void refreshDemandSnapshot() {
        if (snapshotEpoch == tickEpoch && !topologyDirty) {
            return;
        }
        rebuildGroupsIfNeeded();
        totalDemand = 0;
        for (var group : groups) {
            group.demand = 0;
        }
        for (var sink : sinks) {
            sink.demand = sink.receive(Integer.MAX_VALUE, true);
            sink.allocation = 0;
            if (sink.group != null) {
                sink.group.demand += sink.demand;
            }
            totalDemand = saturatingAdd(totalDemand, sink.demand);
        }
        snapshotEpoch = tickEpoch;
    }

    private void rebuildGroupsIfNeeded() {
        if (!topologyDirty) {
            return;
        }
        groups.clear();
        Map<Object, ReceiverGroup> byKey = new LinkedHashMap<>();
        for (var sink : sinks) {
            Object key = sink.groupKey();
            if (key == null) {
                sink.group = null;
                continue;
            }
            ReceiverGroup group = byKey.computeIfAbsent(key, ignored -> {
                var created = new ReceiverGroup();
                groups.add(created);
                return created;
            });
            group.sinks.add(sink);
            sink.group = group;
        }
        topologyDirty = false;
    }

    private void invalidateSnapshot() {
        snapshotEpoch = Long.MIN_VALUE;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private final class ReceiverGroup {
        private final List<SinkEntry> sinks = new ArrayList<>();
        private long demand;
        private int allocation;

        private int distribute() {
            if (allocation <= 0) {
                return 0;
            }
            EnergyDistributionMode mode = sinks.getFirst().mode();
            int accepted = 0;
            if (mode == EnergyDistributionMode.ROUND_ROBIN) {
                accepted = PrioritizedEnergyDistributor.distribute(allocation, sinks.size(),
                        index -> sinks.get(index).demand, (index, amount) -> {
                            var sink = sinks.get(index);
                            int received = sink.receive(amount, false);
                            sink.demand = Math.max(0, sink.demand - received);
                            return received;
                        });
            } else {
                for (var sink : sinks) {
                    if (sink.allocation <= 0) {
                        continue;
                    }
                    int received = sink.receive(sink.allocation, false);
                    sink.demand = Math.max(0, sink.demand - received);
                    accepted += received;
                }
            }
            demand = Math.max(0, demand - accepted);
            return accepted;
        }
    }

    private final class SinkEntry {
        private final IGridNode node;
        private final Object owner;
        private final long joinOrder;
        private ReceiverGroup group;
        private int demand;
        private int allocation;

        private SinkEntry(IGridNode node, Object owner, long joinOrder) {
            this.node = node;
            this.owner = owner;
            this.joinOrder = joinOrder;
        }

        private long joinOrder() {
            return joinOrder;
        }

        private int remainingDemand() {
            return Math.max(0, demand - allocation);
        }

        private int receive(int amount, boolean simulate) {
            if (amount <= 0 || node.getGrid() != grid) {
                return 0;
            }
            if (owner instanceof PatternP2PTunnelPart output) {
                return output.receiveExternalEnergy(amount, simulate);
            }
            if (owner instanceof PatternP2PUnitPortPart port) {
                return port.receiveExternalEnergy(amount, simulate);
            }
            return 0;
        }

        private @Nullable Object groupKey() {
            if (owner instanceof PatternP2PTunnelPart output) {
                return output.getFrequency() == 0 ? null : new OutputGroup(output.getFrequency());
            }
            if (owner instanceof PatternP2PUnitPortPart port) {
                return port.getBoundPatternP2PUnitId() == null
                        ? null : new UnitGroup(port.getBoundPatternP2PUnitId());
            }
            return null;
        }

        private EnergyDistributionMode mode() {
            if (owner instanceof PatternP2PTunnelPart output) {
                return output.getOutputLogic().getEnergyDistributionMode();
            }
            if (owner instanceof PatternP2PUnitPortPart port) {
                PatternP2PUnitManagerPart manager = findManager(port.getBoundPatternP2PUnitId());
                return manager == null ? EnergyDistributionMode.EVEN
                        : manager.getLogic().getEnergyDistributionMode();
            }
            return EnergyDistributionMode.EVEN;
        }
    }

    private record OutputGroup(short frequency) {
    }

    private record UnitGroup(UUID id) {
    }
}
