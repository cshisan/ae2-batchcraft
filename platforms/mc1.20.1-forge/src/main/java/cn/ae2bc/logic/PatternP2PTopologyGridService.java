package cn.ae2bc.logic;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lazily rebuilt indexes for pattern P2P frequencies and unit bindings. */
public final class PatternP2PTopologyGridService implements IGridService, IGridServiceProvider {
    private static final Comparator<PatternP2PTunnelPart> TUNNEL_ORDER = Comparator
            .<PatternP2PTunnelPart, String>comparing(PatternP2PTopologyGridService::levelId)
            .thenComparingLong(part -> part.getBlockEntity().getBlockPos().asLong())
            .thenComparingInt(part -> part.getSide() == null ? -1 : part.getSide().ordinal());
    private static final Comparator<PatternP2PUnitManagerPart> MANAGER_ORDER = Comparator
            .<PatternP2PUnitManagerPart, String>comparing(PatternP2PTopologyGridService::levelId)
            .thenComparingLong(part -> part.getBlockEntity().getBlockPos().asLong());

    private final IGrid grid;
    private final Map<Short, PatternP2PTunnelPart> inputs = new HashMap<>();
    private final Map<Short, List<PatternP2PTunnelPart>> outputs = new HashMap<>();
    private final Map<Short, List<PatternP2PUnitManagerPart>> managersByFrequency = new HashMap<>();
    private final Map<UUID, PatternP2PUnitManagerPart> managersById = new HashMap<>();
    private final Map<UUID, Map<PatternP2PUnitPortType, List<PatternP2PUnitPortPart>>> portsByUnit = new HashMap<>();
    private boolean dirty = true;

    public PatternP2PTopologyGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        dirty = true;
    }

    @Override
    public void removeNode(IGridNode node) {
        dirty = true;
    }

    public void topologyChanged() {
        dirty = true;
    }

    public @Nullable PatternP2PTunnelPart findInput(short frequency) {
        rebuildIfNeeded();
        return frequency == 0 ? null : inputs.get(frequency);
    }

    public List<PatternP2PTunnelPart> getOutputs(short frequency) {
        rebuildIfNeeded();
        return outputs.getOrDefault(frequency, List.of());
    }

    public List<PatternP2PUnitManagerPart> getManagers(short frequency) {
        rebuildIfNeeded();
        return managersByFrequency.getOrDefault(frequency, List.of());
    }

    public @Nullable PatternP2PUnitManagerPart findManager(UUID id) {
        rebuildIfNeeded();
        return id == null ? null : managersById.get(id);
    }

    public List<PatternP2PUnitPortPart> getPorts(UUID unitId, PatternP2PUnitPortType type) {
        rebuildIfNeeded();
        return portsByUnit.getOrDefault(unitId, Map.of()).getOrDefault(type, List.of());
    }

    public List<PatternP2PUnitPortPart> getPorts(UUID unitId) {
        rebuildIfNeeded();
        var byType = portsByUnit.get(unitId);
        if (byType == null) {
            return List.of();
        }
        List<PatternP2PUnitPortPart> result = new ArrayList<>();
        byType.values().forEach(result::addAll);
        return result;
    }

    public List<PatternP2PUnitPortPart> getPortsForFrequency(short frequency, PatternP2PUnitPortType type) {
        rebuildIfNeeded();
        List<PatternP2PUnitPortPart> result = new ArrayList<>();
        for (var manager : managersByFrequency.getOrDefault(frequency, List.of())) {
            result.addAll(getPorts(manager.getPatternP2PUnitId(), type));
        }
        return result;
    }

    private void rebuildIfNeeded() {
        if (!dirty) {
            return;
        }
        dirty = false;
        inputs.clear();
        outputs.clear();
        managersByFrequency.clear();
        managersById.clear();
        portsByUnit.clear();

        for (var tunnel : grid.getMachines(PatternP2PTunnelPart.class)) {
            short frequency = tunnel.getFrequency();
            if (frequency == 0) {
                continue;
            }
            if (tunnel.isOutput()) {
                outputs.computeIfAbsent(frequency, ignored -> new ArrayList<>()).add(tunnel);
            } else {
                var previous = inputs.get(frequency);
                if (previous == null || TUNNEL_ORDER.compare(tunnel, previous) < 0) {
                    inputs.put(frequency, tunnel);
                }
                if (previous != null) {
                    Ae2bcMod.LOGGER.warn("Multiple pattern P2P inputs use frequency {}", Short.toUnsignedInt(frequency));
                }
            }
        }
        outputs.replaceAll((ignored, value) -> value.stream().sorted(TUNNEL_ORDER).toList());

        for (var manager : grid.getMachines(PatternP2PUnitManagerPart.class)) {
            if (manager.getFrequency() != 0) {
                managersByFrequency.computeIfAbsent(manager.getFrequency(), ignored -> new ArrayList<>()).add(manager);
            }
            UUID id = manager.getPatternP2PUnitId();
            var previous = managersById.get(id);
            if (previous == null || MANAGER_ORDER.compare(manager, previous) < 0) {
                managersById.put(id, manager);
            }
            if (previous != null) {
                Ae2bcMod.LOGGER.warn("Multiple pattern P2P unit managers use id {}", id);
            }
        }
        managersByFrequency.replaceAll((ignored, value) -> value.stream().sorted(MANAGER_ORDER).toList());

        for (var port : grid.getMachines(PatternP2PUnitPortPart.class)) {
            UUID id = port.getBoundPatternP2PUnitId();
            if (id != null) {
                portsByUnit.computeIfAbsent(id, ignored -> new EnumMap<>(PatternP2PUnitPortType.class))
                        .computeIfAbsent(port.getType(), ignored -> new ArrayList<>()).add(port);
            }
        }
        portsByUnit.replaceAll((ignored, byType) -> {
            byType.replaceAll((type, ports) -> List.copyOf(ports));
            return Map.copyOf(byType);
        });
    }

    private static String levelId(PatternP2PTunnelPart part) {
        return part.getLevel() == null ? "" : part.getLevel().dimension().location().toString();
    }

    private static String levelId(PatternP2PUnitManagerPart part) {
        return part.getLevel() == null ? "" : part.getLevel().dimension().location().toString();
    }
}
