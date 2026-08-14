package cn.ae2bc.logic;

import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.core.energy.EnergyEndpoint;
import cn.ae2bc.core.cache.InvalidatableValue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/** Invalidatable per-grid topology index for the rv6 adapter. */
public final class PatternP2PTopologyGridService {
    private static final Map<IGrid, InvalidatableValue<WeakReference<Snapshot>>> CACHE =
            new WeakHashMap<IGrid, InvalidatableValue<WeakReference<Snapshot>>>();

    private PatternP2PTopologyGridService() { }

    public static void invalidate(IGridNode node) {
        if (node == null || node.getGrid() == null) return;
        InvalidatableValue<WeakReference<Snapshot>> value = CACHE.get(node.getGrid());
        if (value != null) value.invalidate();
    }

    public static PatternP2PUnitManagerPart find(IGridNode ownNode, UUID id, long ignoredTick) {
        if (ownNode == null || ownNode.getGrid() == null || id == null) return null;
        PatternP2PUnitManagerPart manager = snapshot(ownNode.getGrid()).managers.get(id);
        return manager != null && manager.isOperational() ? manager : null;
    }

    public static List<PatternP2PUnitManagerPart> findByFrequency(
            IGridNode ownNode, short frequency, long ignoredTick) {
        if (ownNode == null || ownNode.getGrid() == null) return Collections.emptyList();
        List<PatternP2PUnitManagerPart> result = new ArrayList<PatternP2PUnitManagerPart>();
        for (PatternP2PUnitManagerPart manager : snapshot(ownNode.getGrid()).managersByFrequency(frequency)) {
            if (manager.isOperational()) result.add(manager);
        }
        return result;
    }

    public static List<PatternP2PUnitManagerPart> findAllByFrequency(
            IGridNode ownNode, short frequency) {
        if (ownNode == null || ownNode.getGrid() == null) return Collections.emptyList();
        return snapshot(ownNode.getGrid()).managersByFrequency(frequency);
    }

    public static List<PatternP2PUnitPortPart> findPorts(IGridNode ownNode, UUID unitId) {
        if (ownNode == null || ownNode.getGrid() == null || unitId == null) {
            return Collections.emptyList();
        }
        return snapshot(ownNode.getGrid()).portsByUnit.getOrDefault(unitId,
                Collections.<PatternP2PUnitPortPart>emptyList());
    }

    public static List<EnergyEndpoint> findEnergyEndpoints(IGridNode ownNode) {
        if (ownNode == null || ownNode.getGrid() == null) return Collections.emptyList();
        List<EnergyEndpoint> result = new ArrayList<EnergyEndpoint>();
        for (EnergyEndpoint endpoint : snapshot(ownNode.getGrid()).energyEndpoints) {
            if (endpoint.isEnergyEndpointAvailable()) result.add(endpoint);
        }
        return result;
    }

    public static List<PatternP2PTunnelEnergyPart> findEnergyParts(IGridNode ownNode) {
        if (ownNode == null || ownNode.getGrid() == null) return Collections.emptyList();
        return snapshot(ownNode.getGrid()).energyParts;
    }

    public static PatternP2PTunnelPart findInput(IGridNode ownNode, short frequency) {
        if (ownNode == null || ownNode.getGrid() == null || frequency == 0) return null;
        PatternP2PTunnelPart input = snapshot(ownNode.getGrid()).inputs.get(frequency);
        return input != null && input.getGridNode() != null && input.getGridNode().isActive()
                ? input : null;
    }

    public static int countByFrequency(IGridNode ownNode, short frequency, long ignoredTick) {
        int count = 0;
        for (PatternP2PUnitManagerPart manager : findAllByFrequency(ownNode, frequency)) {
            if (manager.isOperational()) count++;
        }
        return count;
    }

    private static Snapshot snapshot(IGrid grid) {
        InvalidatableValue<WeakReference<Snapshot>> value = CACHE.get(grid);
        if (value == null) {
            value = new InvalidatableValue<WeakReference<Snapshot>>();
            CACHE.put(grid, value);
        }
        final IGrid owner = grid;
        WeakReference<Snapshot> reference = value.get(() -> new WeakReference<Snapshot>(new Snapshot(owner)));
        Snapshot snapshot = reference.get();
        if (snapshot == null) {
            value.invalidate();
            snapshot = value.get(() -> new WeakReference<Snapshot>(new Snapshot(owner))).get();
        }
        return snapshot;
    }

    private static final class Snapshot {
        private final Map<UUID, PatternP2PUnitManagerPart> managers =
                new HashMap<UUID, PatternP2PUnitManagerPart>();
        private final Map<Short, List<PatternP2PUnitManagerPart>> managersByFrequency =
                new HashMap<Short, List<PatternP2PUnitManagerPart>>();
        private final Map<UUID, List<PatternP2PUnitPortPart>> portsByUnit =
                new HashMap<UUID, List<PatternP2PUnitPortPart>>();
        private final Map<Short, PatternP2PTunnelPart> inputs =
                new HashMap<Short, PatternP2PTunnelPart>();
        private final List<EnergyEndpoint> energyEndpoints = new ArrayList<EnergyEndpoint>();
        private final List<PatternP2PTunnelEnergyPart> energyParts =
                new ArrayList<PatternP2PTunnelEnergyPart>();

        private Snapshot(IGrid grid) {
            for (IGridNode node : grid.getNodes()) {
                Object machine = node.getMachine();
                if (machine instanceof PatternP2PUnitManagerPart) {
                    PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) machine;
                    if (!managers.containsKey(manager.getUnitId())) managers.put(manager.getUnitId(), manager);
                    if (manager.getFrequency() != 0) {
                        managersByFrequency.computeIfAbsent(manager.getFrequency(),
                                ignored -> new ArrayList<PatternP2PUnitManagerPart>()).add(manager);
                    }
                } else if (machine instanceof PatternP2PUnitPortPart) {
                    PatternP2PUnitPortPart port = (PatternP2PUnitPortPart) machine;
                    if (port.getBoundManagerId() != null) {
                        portsByUnit.computeIfAbsent(port.getBoundManagerId(),
                                ignored -> new ArrayList<PatternP2PUnitPortPart>()).add(port);
                    }
                } else if (machine instanceof PatternP2PTunnelPart) {
                    PatternP2PTunnelPart tunnel = (PatternP2PTunnelPart) machine;
                    if (!tunnel.isOutput() && tunnel.getFrequency() != 0
                            && !inputs.containsKey(tunnel.getFrequency())) {
                        inputs.put(tunnel.getFrequency(), tunnel);
                    }
                } else if (machine instanceof PatternP2PTunnelEnergyPart) {
                    PatternP2PTunnelEnergyPart energy = (PatternP2PTunnelEnergyPart) machine;
                    energyParts.add(energy);
                }
                if (machine instanceof EnergyEndpoint) energyEndpoints.add((EnergyEndpoint) machine);
            }
            Comparator<PatternP2PUnitManagerPart> order =
                    Comparator.comparing(manager -> manager.getUnitId().toString());
            for (List<PatternP2PUnitManagerPart> managers : managersByFrequency.values()) {
                managers.sort(order);
            }
        }

        private List<PatternP2PUnitManagerPart> managersByFrequency(short frequency) {
            return managersByFrequency.getOrDefault(frequency,
                    Collections.<PatternP2PUnitManagerPart>emptyList());
        }
    }
}
