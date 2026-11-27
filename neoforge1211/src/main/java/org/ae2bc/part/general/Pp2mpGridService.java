package org.ae2bc.part.general;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.parts.AEBasePart;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.Pp2mpKey;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakePart;
import org.ae2bc.part.task.Pp2mpTaskEndpoint;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Pp2mpGridService implements IGridService, IGridServiceProvider {
    private static final Comparator<AEBasePart> PART_ORDER = Comparator
            .comparing(Pp2mpGridService::levelId)
            .thenComparingLong(part -> part.getBlockEntity().getBlockPos().asLong())
            .thenComparingInt(part -> {
                Direction side = part.getSide();
                return side == null ? -1 : side.ordinal();
            });
    private static final Comparator<AEBasePart> INPUT_ORDER = PART_ORDER;
    private static final Comparator<AEBasePart> OUTPUT_ORDER = PART_ORDER;
    private static final Comparator<Pp2mpUnitManagerPart> MANAGER_ORDER = Comparator
            .<Pp2mpUnitManagerPart, String>comparing(Pp2mpGridService::levelId)
            .thenComparingLong(part -> part.getBlockEntity().getBlockPos().asLong());
    private static final Comparator<AEBasePart> RESOURCE_INTAKE_ORDER = PART_ORDER;

    private final IGrid grid;
    private final @Getter Random unitIdGenerator;
    private final Map<Short, Pp2mpInputPart> inputs = new HashMap<>();
    private final Map<Short, Pp2mpResourceIntakePart> resourceIntakes = new HashMap<>();
    private final Map<Short, FakeCraftingConverterBlockEntity> convertersByFrequency = new HashMap<>();
    private final Set<Short> conflictingConverterFrequencies = new HashSet<>();
    private final Map<Short, List<String>> loggedConverterConflicts = new HashMap<>();
    private final Map<Short, List<Pp2mpOutputPart>> outputs = new HashMap<>();
    private final Map<Short, List<Pp2mpTaskEndpoint>> endpoints = new HashMap<>();
    private final Map<Short, List<Pp2mpUnitManagerPart>> managersByFrequency = new HashMap<>();
    private final Map<Integer, Pp2mpUnitManagerPart> managersByKey = new HashMap<>();
    private final @Getter Set<Short> managerUnitIds = new HashSet<>();
    private final Map<Integer, Map<UnitPortType, List<Pp2mpUnitPortPart>>> ports = new HashMap<>();
    private final Map<Integer, Map<UnitPortType, List<Pp2mpUnitOutputTypePortPart>>> orderedPorts = new HashMap<>();
    private boolean dirty = true;
    private boolean resourceIntakeConfigDirty = true;
    private boolean configBindingsDirty = true;
    private boolean refreshingConfigurations;

    public Pp2mpGridService(IGrid grid) {
        this.grid = grid;
        this.unitIdGenerator = new Random(Integer.rotateLeft(grid.hashCode(), 16));
    }

    public static Pp2mpGridService get(IGrid grid) {
        return grid.getService(Pp2mpGridService.class);
    }

    private static String levelId(AEBasePart part) {
        Level level = part.getLevel();
        return level == null ? "" : level.dimension().location().toString();
    }

    /* 节点归属变化同时使索引和供应配置来源失效。 */
    public void topologyChanged() {
        this.dirty = true;
        this.orderedPorts.clear();
        this.resourceIntakeConfigDirty = true;
        this.configBindingsDirty = true;
    }

    /* 优先级变化仅重建端口顺序，不重新扫描整个网格。 */
    public void portOrderChanged() {
        this.orderedPorts.clear();
    }

    public List<Pp2mpUnitOutputTypePortPart> getOutputPorts(short frequency, short unitId, UnitPortType type) {
        this.rebuildIfNeeded();
        return this.orderedPorts.computeIfAbsent(Pp2mpKey.key(frequency, unitId), ignored -> new EnumMap<>(UnitPortType.class))
                .computeIfAbsent(type, ignored -> this.getPorts(frequency, unitId, type).stream()
                        .filter(Pp2mpUnitOutputTypePortPart.class::isInstance)
                        .map(Pp2mpUnitOutputTypePortPart.class::cast)
                        .sorted(Comparator.comparingInt(
                                (Pp2mpUnitOutputTypePortPart port) -> port.getData().getPriority()).reversed())
                        .toList());
    }

    /* 配置变更只标记供应同步，不因库存收发重复扫描所有端点。 */
    public void resourceIntakeConfigChanged() {
        this.resourceIntakeConfigDirty = true;
    }

    /* 同频率存在多个资源接收端时固定选择配置源，避免本地配置反复被不同来源覆盖。 */
    public @Nullable Pp2mpResourceIntakePart findResourceIntake(short frequency) {
        this.rebuildIfNeeded();
        return frequency == 0 ? null : this.resourceIntakes.get(frequency);
    }

    /* 网络拓扑或供应规则变化后统一更新，即使当前没有任务和资源传输也会同步。 */
    @Override
    public void onServerEndTick() {
        this.refreshConfigurations();
    }

    /* 合并拓扑与规则变更，稳定状态只检查标志；调度前也可安全调用。 */
    public void refreshConfigurations() {
        if (this.refreshingConfigurations || !this.configBindingsDirty && !this.resourceIntakeConfigDirty) {
            return;
        }
        this.refreshingConfigurations = true;
        try {
            this.rebuildIfNeeded();
            boolean bindingsChanged = this.configBindingsDirty;
            this.configBindingsDirty = false;
            this.resourceIntakeConfigDirty = false;
            for (List<Pp2mpOutputPart> parts : this.outputs.values()) {
                for (Pp2mpOutputPart part : parts) {
                    if (bindingsChanged) {
                        part.clearReceiveTargetCache();
                        part.refreshConfigRegister();
                        part.wakeProductExtractor();
                    } else {
                        part.refreshProviderFilter();
                    }
                }
            }
            for (List<Pp2mpUnitManagerPart> parts : this.managersByFrequency.values()) {
                for (Pp2mpUnitManagerPart part : parts) {
                    if (bindingsChanged) {
                        part.clearReceiveStorageCache();
                        part.refreshConfigEventRegister();
                        part.getLogic().alertPendingRetry();
                    } else {
                        part.refreshProviderFilter();
                    }
                }
            }
        } finally {
            this.refreshingConfigurations = false;
        }
    }

    public @Nullable Pp2mpInputPart findInput(short frequency) {
        rebuildIfNeeded();
        return frequency == 0 ? null : inputs.get(frequency);
    }

    /* 端点空闲后唤醒同频输入端，下一次 tick 继续领取单批任务。 */
    public void wakeBatchInput(short frequency) {
        if (frequency == 0) {
            return;
        }
        Pp2mpInputPart input = this.findInput(frequency);
        if (input != null && input.getData().hasBatchTask()) {
            input.getLogic().wakeBatchDispatcher();
        }
    }

    public List<Pp2mpOutputPart> getOutputs(short frequency) {
        rebuildIfNeeded();
        return outputs.getOrDefault(frequency, List.of());
    }

    public List<Pp2mpTaskEndpoint> getEndpoints(short frequency) {
        this.refreshConfigurations();
        return this.endpoints.getOrDefault(frequency, List.of());
    }

    public List<Pp2mpUnitManagerPart> getManagers(short frequency) {
        rebuildIfNeeded();
        return managersByFrequency.getOrDefault(frequency, List.of());
    }

    /* 合网出现同频转换器时暂停新任务，不按节点加载顺序选择其中一台。 */
    public @Nullable FakeCraftingConverterBlockEntity findFakeCraftingConverter(short frequency) {
        this.rebuildIfNeeded();
        return frequency == 0 || this.conflictingConverterFrequencies.contains(frequency)
                ? null : this.convertersByFrequency.get(frequency);
    }

    /* 加载频率前检查占用，未通电但仍在本网络中的转换器同样占用频率。 */
    public boolean isFakeCraftingFrequencyUsed(short frequency, FakeCraftingConverterBlockEntity excluded) {
        this.rebuildIfNeeded();
        FakeCraftingConverterBlockEntity existing = this.convertersByFrequency.get(frequency);
        return frequency != 0 && (this.conflictingConverterFrequencies.contains(frequency)
                || existing != null && existing != excluded);
    }

    /* 查找绑定到指定频率和单元 ID 的管理器。 */
    public @Nullable Pp2mpUnitManagerPart findManager(short frequency, short unitId) {
        this.rebuildIfNeeded();
        return frequency == 0 || unitId == 0
                ? null
                : this.managersByKey.get(Pp2mpKey.key(frequency, unitId));
    }

    /* 拓扑待校准时由统一刷新唤醒，避免每个端口通知都立即重建索引。 */
    public void wakeUnitManager(short frequency, short unitId) {
        if (this.configBindingsDirty) {
            return;
        }
        Pp2mpUnitManagerPart manager = this.findManager(frequency, unitId);
        if (manager != null) {
            manager.getLogic().alertPendingRetry();
        }
    }

    /* 返回库存变化只唤醒端点，能力缓存由目标自身的失效通知刷新。 */
    public void wakeReturnTargets(short frequency) {
        this.rebuildIfNeeded();
        for (Pp2mpOutputPart output : this.outputs.getOrDefault(frequency, List.of())) {
            output.wakeProductExtractor();
        }
        for (Pp2mpUnitManagerPart manager : this.managersByFrequency.getOrDefault(frequency, List.of())) {
            manager.getLogic().alertPendingRetry();
        }
    }

    /* 返回稳定的只读拓扑快照，仅拓扑重建会替换非空列表。 */
    public List<Pp2mpUnitPortPart> getPorts(short frequency, short unitId, UnitPortType type) {
        this.rebuildIfNeeded();
        return this.ports.getOrDefault(
                Pp2mpKey.key(frequency, unitId), Map.of()
        ).getOrDefault(type, List.of());
    }

    public List<Pp2mpUnitPortPart> getPorts(short frequency, short unitId) {
        this.rebuildIfNeeded();
        Map<UnitPortType, List<Pp2mpUnitPortPart>> byType = this.ports.getOrDefault(
                Pp2mpKey.key(frequency, unitId), Map.of()
        );
        return byType.values().stream().flatMap(List::stream).toList();
    }

    public void rebuildIfNeeded() {
        if (!this.dirty) {
            return;
        }
        this.dirty = false;
        this.inputs.clear();
        this.resourceIntakes.clear();
        this.convertersByFrequency.clear();
        this.conflictingConverterFrequencies.clear();
        this.outputs.clear();
        this.endpoints.clear();
        this.managersByFrequency.clear();
        this.managersByKey.clear();
        this.managerUnitIds.clear();
        this.ports.clear();

        for (Pp2mpInputPart tunnel : this.grid.getMachines(Pp2mpInputPart.class)) {
            short frequency = tunnel.getFrequency();
            if (frequency == 0) {
                continue;
            }
            Pp2mpInputPart previous = this.inputs.get(frequency);
            if (previous == null || INPUT_ORDER.compare(tunnel, previous) < 0) {
                this.inputs.put(frequency, tunnel);
            }
            if (previous != null) {
                Ae2bcMod.LOGGER.warn("Multiple pattern P2P inputs use frequency {}", Short.toUnsignedInt(frequency));
            }
        }
        for (Pp2mpResourceIntakePart resourceIntake : this.grid.getMachines(Pp2mpResourceIntakePart.class)) {
            short frequency = resourceIntake.getFrequency();
            if (frequency == 0) {
                continue;
            }
            Pp2mpResourceIntakePart previous = this.resourceIntakes.get(frequency);
            if (previous == null || Pp2mpGridService.RESOURCE_INTAKE_ORDER.compare(resourceIntake, previous) < 0) {
                this.resourceIntakes.put(frequency, resourceIntake);
            }
        }
        for (FakeCraftingConverterBlockEntity converter : this.grid.getMachines(
                FakeCraftingConverterBlockEntity.class
        )) {
            short frequency = converter.getFrequency();
            if (frequency != 0 && this.convertersByFrequency.putIfAbsent(frequency, converter) != null) {
                this.conflictingConverterFrequencies.add(frequency);
            }
        }
        this.logConverterConflicts();
        for (Pp2mpOutputPart tunnel : this.grid.getMachines(Pp2mpOutputPart.class)) {
            short frequency = tunnel.getFrequency();
            if (frequency == 0) {
                continue;
            }
            this.outputs.computeIfAbsent(frequency, ignored -> new ArrayList<>()).add(tunnel);
        }
        this.outputs.replaceAll((ignored, value) -> value.stream().sorted(OUTPUT_ORDER).toList());

        for (Pp2mpUnitManagerPart manager : this.grid.getMachines(Pp2mpUnitManagerPart.class)) {
            if (manager.getFrequency() != 0) {
                this.managersByFrequency.computeIfAbsent(manager.getFrequency(), ignored -> new ArrayList<>()).add(manager);
            }
            short unitId = manager.getUnitId();
            if (unitId == 0) {
                continue;
            }
            if (!this.managerUnitIds.add(unitId)) {
                Ae2bcMod.LOGGER.warn(
                        "Multiple pattern P2P unit managers use unitId {}",
                        Short.toUnsignedInt(unitId)
                );
            }
            short frequency = manager.getFrequency();
            if (frequency == 0) {
                continue;
            }
            int key = Pp2mpKey.key(frequency, unitId);
            Pp2mpUnitManagerPart previous = this.managersByKey.get(key);
            if (previous == null || MANAGER_ORDER.compare(manager, previous) < 0) {
                this.managersByKey.put(key, manager);
            }
            if (previous != null) {
                Ae2bcMod.LOGGER.warn(
                        "Multiple pattern P2P unit managers use frequency {} and unitId {}",
                        Short.toUnsignedInt(frequency), Short.toUnsignedInt(unitId)
                );
            }
        }
        this.managersByFrequency.replaceAll((ignored, value) -> value.stream().sorted(MANAGER_ORDER).toList());
        this.outputs.forEach((frequency, parts) -> {
            List<Pp2mpTaskEndpoint> targets = this.endpoints.computeIfAbsent(frequency, ignored -> new ArrayList<>());
            for (Pp2mpOutputPart part : parts) {
                targets.add(part.getLogic());
            }
        });
        this.managersByFrequency.forEach((frequency, parts) -> {
            List<Pp2mpTaskEndpoint> targets = this.endpoints.computeIfAbsent(frequency, ignored -> new ArrayList<>());
            for (Pp2mpUnitManagerPart part : parts) {
                targets.add(part.getLogic());
            }
        });
        this.endpoints.replaceAll((frequency, targets) -> List.copyOf(targets));

        for (Class<?> machineClass : this.grid.getMachineClasses()) {
            if (!Pp2mpUnitPortPart.class.isAssignableFrom(machineClass)) {
                continue;
            }

            for (IGridNode gridNode : this.grid.getMachineNodes(machineClass)) {
                if (!(gridNode.getOwner() instanceof Pp2mpUnitPortPart port)) {
                    continue;
                }

                if (port.getFrequency() == 0 || port.getUnitId() == 0) {
                    continue;
                }

                int key = Pp2mpKey.key(port.getFrequency(), port.getUnitId());
                this.ports.computeIfAbsent(key, ignored -> new EnumMap<>(UnitPortType.class))
                        .computeIfAbsent(port.getType(), ignored -> new ArrayList<>())
                        .add(port);
            }
        }
        for (Map<UnitPortType, List<Pp2mpUnitPortPart>> byType : this.ports.values()) {
            byType.replaceAll((type, parts) -> List.copyOf(parts));
        }
    }

    /* 仅在冲突出现、涉及节点变化或冲突解除时记录，避免拓扑刷新反复刷屏。 */
    private void logConverterConflicts() {
        if (this.conflictingConverterFrequencies.isEmpty() && this.loggedConverterConflicts.isEmpty()) {
            return;
        }
        Map<Short, List<String>> current = new HashMap<>();
        for (FakeCraftingConverterBlockEntity converter : this.grid.getMachines(FakeCraftingConverterBlockEntity.class)) {
            short frequency = converter.getFrequency();
            if (this.conflictingConverterFrequencies.contains(frequency)) {
                current.computeIfAbsent(frequency, ignored -> new ArrayList<>())
                        .add(Pp2mpGridService.converterLocation(converter));
            }
        }
        String gridId = Integer.toHexString(System.identityHashCode(this.grid));
        for (Map.Entry<Short, List<String>> entry : current.entrySet()) {
            List<String> locations = entry.getValue();
            locations.sort(Comparator.naturalOrder());
            if (!locations.equals(this.loggedConverterConflicts.get(entry.getKey()))) {
                Ae2bcMod.LOGGER.warn(
                        "Fake crafting converter frequency conflict: grid={}, frequency={}, converters={}. "
                                + "New fake crafting task dispatch is paused; existing tickets retain their owners. "
                                + "Assign different frequencies or separate the networks.",
                        gridId, Short.toUnsignedInt(entry.getKey()), locations);
            }
        }
        for (Short frequency : this.loggedConverterConflicts.keySet()) {
            if (!current.containsKey(frequency)) {
                FakeCraftingConverterBlockEntity remaining = this.convertersByFrequency.get(frequency);
                Ae2bcMod.LOGGER.info(
                        "Fake crafting converter frequency conflict cleared: grid={}, frequency={}, remainingConverter={}",
                        gridId, Short.toUnsignedInt(frequency),
                        remaining == null ? "none" : Pp2mpGridService.converterLocation(remaining));
            }
        }
        this.loggedConverterConflicts.clear();
        this.loggedConverterConflicts.putAll(current);
    }

    /* 记录维度、坐标和持久身份，区分不同网络及同位置重新放置的转换器。 */
    private static String converterLocation(FakeCraftingConverterBlockEntity converter) {
        Level level = converter.getLevel();
        String dimension = level == null ? "unknown" : level.dimension().location().toString();
        return "%s [%s] id=%s".formatted(
                dimension, converter.getBlockPos().toShortString(), converter.getConverterId());
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        this.topologyChanged();
        IGridServiceProvider.super.addNode(gridNode, savedData);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (gridNode.getOwner() instanceof Pp2mpOutputPart output) {
            output.clearConfigBinding();
        } else if (gridNode.getOwner() instanceof Pp2mpUnitManagerPart manager) {
            manager.clearConfigBinding();
        }
        this.topologyChanged();
        IGridServiceProvider.super.removeNode(gridNode);
    }
}
