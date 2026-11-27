package org.ae2bc.part.unit.manager;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.core.unit.RedstonePortParam;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.task.Pp2mpTaskLogic;
import org.ae2bc.part.task.Pp2mpTaskRecord;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.part.general.Pp2mpTickWake;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.part.*;
import org.ae2bc.pattern.*;
import org.ae2bc.storage.PendingMaterial;
import org.ae2bc.storage.ProductExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Pp2mpUnitManagerLogic extends Pp2mpTaskLogic {
    private final IManagedGridNode mainNode;
    private final Pp2mpUnitManagerPart part;
    private final Pp2mpUnitManagerData data;
    private final Pp2mpTickWake tickWake = new Pp2mpTickWake();
    private final Map<Pp2mpUnitExtractPortPart, Integer> extractionCooldowns = new IdentityHashMap<>();
    private final Map<Pp2mpUnitCollectPortPart, Integer> collectionCooldowns = new IdentityHashMap<>();
    private final Map<Pp2mpUnitBreakPortPart, Integer> breakCooldowns = new IdentityHashMap<>();
    private final Map<UnitPortType, List<Pp2mpUnitPortPart>> cooldownPorts = new EnumMap<>(UnitPortType.class);
    private int providerCursor;
    private boolean redstoneRunning;

    public Pp2mpUnitManagerLogic(IManagedGridNode mainNode, Pp2mpUnitManagerPart part) {
        super(part, mainNode::getGrid, part.getData(), part::getReceiveInventory);
        this.mainNode = mainNode;
        this.part = part;
        this.data = part.getData();
    }

    @Override
    protected boolean hasPendingInputs() {
        return !this.data.getPlannedSend().isEmpty();
    }

    @Override
    public @Nullable String getEndpointId() {
        return this.createEndpointId("unit_manager", true);
    }

    @Override
    public boolean isOperational() {
        return this.mainNode.isActive() && this.part.validFrequency() && this.part.getLevel() instanceof ServerLevel;
    }

    public boolean isTaskOperational() {
        return this.data.isTaskActive() && !this.data.isCancelRequested()
                && this.mainNode.isActive() && this.part.validFrequency();
    }

    /* 按共享预算规划供应端口；模拟不推进游标，超出预算的端口留待后续访问。 */
    public ResourceTransferPlan planProvider(AEKey key, long amount, WorkBudget budget, boolean advance) {
        if (!budget.tryUse() || key == null || amount <= 0 || !this.isOperational()) {
            return ResourceTransferPlan.empty();
        }
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return ResourceTransferPlan.empty();
        }
        List<Pp2mpUnitPortPart> ports = Pp2mpGridService.get(grid)
                .getPorts(this.part.getFrequency(), this.part.getUnitId(), UnitPortType.PROVIDER);
        List<ResourceTransferPlan> plans = new ArrayList<>();
        long remaining = amount;
        int start = ports.isEmpty() ? 0 : Math.floorMod(this.providerCursor, ports.size());
        int visited = 0;
        while (visited < ports.size() && remaining > 0 && budget.remaining() > 0) {
            Pp2mpUnitPortPart port = ports.get((start + visited) % ports.size());
            visited++;
            if (port instanceof Pp2mpUnitProviderPortPart provider) {
                ResourceTransferPlan plan = provider.planProvider(key, remaining, budget);
                plans.add(plan);
                remaining -= plan.amount();
            } else {
                budget.tryUse();
            }
        }
        if (advance) {
            this.providerCursor = visited < ports.size() && remaining > 0 ? start + visited : 0;
        }
        return ResourceTransferPlan.combine(amount, plans, false);
    }


    @Override
    public boolean canAcceptTask() {
        this.refreshConfigurations();
        boolean operational = this.isOperational();
        boolean accepted = operational && this.canStartTask(this.data.getConfig().general().blocking());
        if (!accepted && Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Task] manager unavailable taskId={} operational={} active={} pendingInputs={} tasks={} mainNodeActive={} frequency={} serverLevel={}",
                    this.getEndpointId(),
                    operational,
                    this.data.isTaskActive(),
                    this.data.getPlannedSend().size(),
                    this.data.getTasks().size(),
                    this.mainNode.isActive(),
                    Short.toUnsignedInt(this.part.getFrequency()),
                    this.part.getLevel() instanceof ServerLevel
            );
        }
        return accepted;
    }

    /* 为本次完整交付分配端口，并直接按样板记录产物数量。 */
    @Override
    public boolean tryAcceptTask(IPatternDetails pattern, KeyCounter[] inputs, IActionSource source, UUID taskId) {
        if (this.hasAcceptedTask(taskId)) {
            return true;
        }
        boolean available = pattern != null && inputs != null && taskId != null
                && this.canAcceptTask();
        if (!available) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug(
                        "[P2MP Task] manager rejected before planning endpointId={} pattern={} inputs={} canAcceptTask={}",
                        this.getEndpointId(), pattern != null, inputs != null, false
                );
            }
            return false;
        }

        FakeCraftingConverterBlockEntity converter = this.findFakeTaskConverter(pattern, taskId);
        NavigableMap<Integer, MaterialOutputMode> emptySlots = new TreeMap<>();
        List<PendingMaterial> planSend = this.planSend(pattern, inputs, taskId, converter, emptySlots);
        Map<String, Pp2mpPortAssignment> assignments = new HashMap<>();
        if (planSend.isEmpty() || !this.canAllocate(planSend, emptySlots, assignments)) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug(
                        "[P2MP Task] manager rejected allocation endpointId={} planEmpty={}",
                        this.getEndpointId(), planSend.isEmpty()
                );
            }
            return false;
        }
        Pp2mpTaskBatch.OutputPlan planReceive = Pp2mpTaskBatch.planOutputs(
                pattern, Pp2mpTaskLogic.getFakeTaskOutputKeys(converter, taskId));
        if (planReceive == null) {
            return false;
        }
        if (Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Task] manager accepted endpointId={} inputMaterials={} outputTypes={}",
                    this.getEndpointId(), planSend.size(), planReceive.pending().size()
            );
        }

        this.data.getPlannedSend().clear();
        this.data.getPlannedSend().addAll(planSend);
        this.data.getPortAssignments().clear();
        assignments.values().forEach(Pp2mpPortAssignment::beginDelivery);
        this.data.getPortAssignments().putAll(assignments);
        this.beginTask(planReceive, taskId, converter);
        return true;
    }

    /* 按原始槽位交错分配材料和空槽，所有检查通过后才由接单流程提交绑定。 */
    private boolean canAllocate(
            List<PendingMaterial> materials, NavigableMap<Integer, MaterialOutputMode> emptySlots,
            Map<String, Pp2mpPortAssignment> assignments
    ) {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        // 接单时冻结现有输出端口模式，配置变更从下一笔任务生效。
        Set<UnitPortType> outputTypes = EnumSet.noneOf(UnitPortType.class);
        for (PendingMaterial material : materials) {
            outputTypes.add(material.mode().matchPort());
        }
        for (MaterialOutputMode mode : emptySlots.values()) {
            outputTypes.add(mode.matchPort());
        }
        for (UnitPortType type : outputTypes) {
            for (Pp2mpUnitOutputTypePortPart port : gridService.getOutputPorts(
                    this.part.getFrequency(), this.part.getUnitId(), type)) {
                assignments.put(port.getEndpointId(), new Pp2mpPortAssignment(this.outputMode(port)));
            }
        }
        Pp2mpInsertPlan capacity = new Pp2mpInsertPlan();
        int lastSlot = materials.getLast().slot();
        if (!emptySlots.isEmpty()) {
            lastSlot = Math.max(lastSlot, emptySlots.lastKey());
        }
        int materialIndex = 0;
        for (int slot = 0; slot <= lastSlot; slot++) {
            MaterialOutputMode emptyMode = emptySlots.get(slot);
            if (emptyMode != null && !this.allocateEmptySlot(gridService, slot, emptyMode, assignments)) {
                return false;
            }
            while (materialIndex < materials.size() && materials.get(materialIndex).slot() == slot) {
                if (!this.canAllocateMaterial(gridService, materials.get(materialIndex), capacity,
                        assignments)) {
                    return false;
                }
                materialIndex++;
            }
        }
        return materialIndex == materials.size();
    }

    /* 空槽只占用对应类型的独占端口，不探测库存或触发世界操作。 */
    private boolean allocateEmptySlot(
            Pp2mpGridService gridService, int slot, MaterialOutputMode mode,
            Map<String, Pp2mpPortAssignment> assignments
    ) {
        List<Pp2mpUnitOutputTypePortPart> ports = gridService.getOutputPorts(
                this.part.getFrequency(), this.part.getUnitId(), mode.matchPort());
        if (ports.isEmpty()) {
            return this.data.getConfig().unit().outputPortMode() != OutputPortMode.SLOT_EXCLUSIVE;
        }
        boolean hasExclusivePort = false;
        for (Pp2mpUnitOutputTypePortPart port : ports) {
            Pp2mpPortAssignment assignment = Objects.requireNonNull(assignments.get(port.getEndpointId()));
            if (assignment.getMode() == OutputPortMode.SLOT_EXCLUSIVE) {
                hasExclusivePort = true;
                if (port.isBoundTo(this.part) && assignment.reserveEmpty(slot)) {
                    return true;
                }
            }
        }
        return !hasExclusivePort;
    }

    /* 按数量预检整笔额度；传输端口共享容量预留，世界投料允许逐步完成。 */
    private boolean canAllocateMaterial(
            Pp2mpGridService gridService, PendingMaterial material, Pp2mpInsertPlan capacity,
            Map<String, Pp2mpPortAssignment> assignments
    ) {
        long remaining = material.stack().amount();
        for (Pp2mpUnitOutputTypePortPart port : this.getOutputPorts(gridService, material)) {
            Pp2mpPortAssignment assignment = Objects.requireNonNull(assignments.get(port.getEndpointId()));
            long requested = assignment.requested(material, remaining);
            if (requested <= 0) {
                continue;
            }
            GenericStack stack = new GenericStack(material.stack().what(), requested);
            long inserted = port instanceof Pp2mpUnitTransferPortPart transfer
                    ? transfer.reserve(this.part, stack, material.mode(), capacity)
                    : port.output(this.part, stack, material.mode(), Actionable.SIMULATE);
            if (inserted <= 0) {
                continue;
            }
            long allocated = port instanceof Pp2mpUnitTransferPortPart ? Math.min(inserted, requested) : requested;
            assignment.record(material, allocated);
            remaining -= allocated;
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    private List<PendingMaterial> planSend(
            IPatternDetails pattern, KeyCounter[] inputs, UUID taskId,
            @Nullable FakeCraftingConverterBlockEntity converter,
            NavigableMap<Integer, MaterialOutputMode> emptySlots
    ) {
        List<PendingMaterial> result = new ArrayList<>();
        MaterialOutputAttr outputConfig = MaterialOutputModeResolver.attr(pattern);

        IPatternDetails delegate = pattern instanceof Pp2mpPatternDetails details ? details.getDelegate() : pattern;
        if (delegate instanceof AEProcessingPattern processingPattern) {
            List<Pp2mpPatternSlotMapper.SlotInput> mapped = Pp2mpPatternSlotMapper.map(
                    processingPattern, inputs);
            if (mapped == null) {
                return result;
            }
            List<GenericStack> sparse = processingPattern.getSparseInputs();
            int lastSlot = sparse.size() - 1;
            while (lastSlot >= 0 && sparse.get(lastSlot) == null) {
                lastSlot--;
            }
            for (int slot = 0; slot < lastSlot; slot++) {
                if (sparse.get(slot) == null) {
                    MaterialOutputMode mode = outputConfig.mode(slot);
                    emptySlots.put(slot, mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER
                            ? MaterialOutputMode.NORMAL : mode);
                }
            }
            for (Pp2mpPatternSlotMapper.SlotInput input : mapped) {
                if (outputConfig.mode(input.slot()) == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                    continue;
                }
                result.add(new PendingMaterial(
                        input.stack(), outputConfig.mode(input.slot()), input.slot()));
            }
            return result;
        }

        KeyCounter[] actualInputs = Pp2mpTaskLogic.filterFakeTaskInputs(converter, inputs, taskId);
        for (int slot = 0; slot < actualInputs.length; slot++) {
            KeyCounter counter = actualInputs[slot];
            if (counter == null) {
                continue;
            }

            MaterialOutputMode mode = outputConfig.mode(slot);

            for (Map.Entry<AEKey, Long> entry : counter) {
                if (entry.getValue() > 0) {
                    GenericStack stack = new GenericStack(entry.getKey(), entry.getValue());
                    result.add(new PendingMaterial(stack, mode, slot));
                }
            }
        }

        return result;
    }

    public boolean canAcceptReturned(AEKey key) {
        return this.isTaskOperational() && this.allowsReturned(key);
    }

    @Override
    protected ReturnMode returnMode() {
        return this.data.getConfig().general().returnMode();
    }

    @Override
    protected void taskEnded() {
        if (!this.hasPendingInputs()) {
            this.data.getPortAssignments().clear();
        }
        if (!this.data.isTaskActive()) {
            this.clearRedstonePower();
        }
    }

    @Override
    protected void taskChanged() {
        this.alertPendingRetry();
        IGrid grid = this.mainNode.getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).wakeBatchInput(this.part.getFrequency());
        }
    }

    @Override
    protected void recoverPendingInputs() {
        for (PendingMaterial material : this.data.getPlannedSend()) {
            this.data.getInputRecovery().merge(material.stack().what(), material.stack().amount(), Math::addExact);
        }
        this.data.getPlannedSend().clear();
        this.data.getPortAssignments().clear();
        this.clearRedstonePower();
    }

    /* 每次成功发送立即扣账，后续端口异常也不能重送已交付材料。 */
    private boolean dispatchPending() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return false;
        }

        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        boolean changed = false;

        try {
            for (ListIterator<PendingMaterial> iterator = this.data.getPlannedSend().listIterator(); iterator.hasNext(); ) {
                PendingMaterial pending = iterator.next();
                long remaining = pending.stack().amount();
                List<Pp2mpUnitOutputTypePortPart> ports = this.getOutputPorts(gridService, pending);
                for (Pp2mpUnitOutputTypePortPart outputPort : ports) {
                    if (remaining <= 0) {
                        break;
                    }
                    String portId = outputPort.getEndpointId();
                    Pp2mpPortAssignment assignment = this.data.getPortAssignments().get(portId);
                    if (assignment == null) {
                        assignment = new Pp2mpPortAssignment(this.outputMode(outputPort));
                    }
                    long requested = assignment.requested(pending, remaining);
                    if (requested <= 0) {
                        continue;
                    }
                    GenericStack stack = new GenericStack(pending.stack().what(), requested);
                    long inserted = outputPort.output(this.part, stack, pending.mode(), Actionable.MODULATE);
                    if (inserted <= 0) {
                        continue;
                    }

                    long delivered = Math.min(inserted, requested);
                    remaining -= delivered;
                    assignment.record(pending, delivered);
                    this.data.getPortAssignments().putIfAbsent(portId, assignment);
                    if (remaining == 0) {
                        iterator.remove();
                    } else {
                        iterator.set(new PendingMaterial(
                                new GenericStack(pending.stack().what(), remaining),
                                pending.mode(), pending.slot()));
                    }
                    changed = true;
                }
            }
        } finally {
            if (changed) {
                if (!this.hasPendingInputs()) {
                    this.data.getPortAssignments().clear();
                }
                this.part.getHost().markForSave();
            }
        }
        this.finishTaskIfComplete();
        if (changed && !this.hasPendingInputs()) {
            this.taskChanged();
        }
        return changed;
    }

    /* 按优先级降序返回输出端口；优先级相同时保留网络顺序。 */
    private List<Pp2mpUnitOutputTypePortPart> getOutputPorts(
            Pp2mpGridService gridService,
            PendingMaterial material
    ) {
        return gridService.getOutputPorts(this.part.getFrequency(), this.part.getUnitId(), material.mode().matchPort());
    }

    /* 同步只负责覆盖本地值，调度统一读取端口已经应用的模式。 */
    private OutputPortMode outputMode(Pp2mpUnitOutputTypePortPart port) {
        return port.getData().getOutputPortMode();
    }

    /* 根据管理器持有的任务时钟更新所有已绑定的红石端口。 */
    private boolean tickRedstone() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null || !(this.part.getLevel() instanceof ServerLevel level)) {
            this.clearRedstonePower();
            return false;
        }

        List<Pp2mpUnitPortPart> ports = Pp2mpGridService.get(grid)
                .getPorts(
                        this.part.getFrequency(),
                        this.part.getUnitId(),
                        UnitPortType.REDSTONE
                );
        if (ports.isEmpty()) {
            return false;
        }

        RedstonePortParam param = this.data.getConfig().unit().redstonePortParam();
        int power = 0;
        boolean clockStarted = false;
        boolean pulsePending = false;
        for (Pp2mpTaskRecord record : this.data.getTasks().values()) {
            if (record.getRedstoneStart() == Long.MIN_VALUE) {
                record.setRedstoneStart(level.getGameTime());
                clockStarted = true;
            }
            long activeTicks = Math.max(0, level.getGameTime() - record.getRedstoneStart());
            power = Math.max(power, param.powerAt(activeTicks));
            pulsePending |= activeTicks <= param.getPulseWidth();
        }
        if (clockStarted) {
            this.part.getHost().markForSave();
        }
        boolean changed = false;
        this.redstoneRunning = true;
        for (Pp2mpUnitPortPart candidate : ports) {
            if (candidate instanceof Pp2mpUnitRedstonePortPart port) {
                changed |= port.updatePower(power);
            }
        }

        return switch (param.getMode()) {
            case PULSE -> true;
            case ONCE -> pulsePending || changed;
            case CONTINUOUS -> changed;
        };
    }

    private void clearRedstonePower() {
        this.redstoneRunning = false;
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }
        for (Pp2mpUnitPortPart port : Pp2mpGridService.get(grid)
                .getPorts(
                        this.part.getFrequency(),
                        this.part.getUnitId(),
                        UnitPortType.REDSTONE
                )) {
            if (port instanceof Pp2mpUnitRedstonePortPart redstonePort) {
                redstonePort.resetPower();
            }
        }
    }

    public void alertPendingRetry() {
        this.tickWake.alert(this.mainNode);
    }

    void resetTickWake() {
        this.tickWake.reset();
    }

    public TickRateModulation tick(int ticksSinceLastCall) {
        this.resetTickWake();
        this.refreshConfigurations();
        this.drainTaskRecovery();
        boolean hadTask = this.data.isTaskActive();
        this.finishTaskIfComplete();
        if (hadTask && !this.data.isTaskActive()) {
            return TickRateModulation.URGENT;
        }
        if (!this.isTaskOperational()) {
            if (this.redstoneRunning) {
                this.clearRedstonePower();
            }
            this.extractionCooldowns.clear();
            this.collectionCooldowns.clear();
            this.breakCooldowns.clear();
            this.cooldownPorts.clear();
            return this.hasTaskState() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        boolean redstone = this.tickRedstone();
        boolean changed = !this.data.getPlannedSend().isEmpty() && this.dispatchPending();
        boolean extracted = this.tickExtract(ticksSinceLastCall);
        boolean collected = this.tickCollect(ticksSinceLastCall);
        boolean broken = this.tickBreak(ticksSinceLastCall);
        return redstone || changed || extracted || collected || broken
                ? TickRateModulation.URGENT
                : TickRateModulation.SLOWER;
    }

    private boolean tickBreak(int ticksSinceLastCall) {
        List<Pp2mpUnitPortPart> ports = this.activePorts(UnitPortType.BREAK, this.breakCooldowns);
        if (ports.isEmpty()) {
            return false;
        }

        boolean breakPickup = this.data.getConfig().unit().breakPickup();
        boolean changed = false;
        for (Pp2mpUnitPortPart candidate : ports) {
            if (!(candidate instanceof Pp2mpUnitBreakPortPart port)) {
                continue;
            }
            int cooldown = this.breakCooldowns.getOrDefault(port, 0) - ticksSinceLastCall;
            if (cooldown > 0) {
                this.breakCooldowns.put(port, cooldown);
                continue;
            }

            changed |= port.breakAdjacentBlock(breakPickup);
            this.breakCooldowns.put(port, 1);
        }
        return changed;
    }

    private boolean tickExtract(int ticksSinceLastCall) {
        List<Pp2mpUnitPortPart> ports = this.activePorts(UnitPortType.EXTRACT, this.extractionCooldowns);
        if (ports.isEmpty()) {
            return false;
        }

        boolean changed = false;
        ProductExtractionParam extractionParam = this.data.getConfig().general().extractionParam();
        for (Pp2mpUnitPortPart candidate : ports) {
            if (!(candidate instanceof Pp2mpUnitExtractPortPart port)) {
                continue;
            }
            int cooldown = this.extractionCooldowns.getOrDefault(port, 0) - ticksSinceLastCall;
            if (cooldown > 0) {
                this.extractionCooldowns.put(port, cooldown);
                continue;
            }

            ProductExtractor.Result result = port.extractProducts(extractionParam);
            this.extractionCooldowns.put(port, Math.max(1, extractionParam.getInterval()));
            changed |= result.movedOperations() > 0;
        }
        return changed;
    }

    private boolean tickCollect(int ticksSinceLastCall) {
        List<Pp2mpUnitPortPart> ports = this.activePorts(UnitPortType.COLLECT, this.collectionCooldowns);
        if (ports.isEmpty()) {
            return false;
        }

        boolean changed = false;
        int quantity = this.data.getConfig().general().extractionParam().getQuantity();
        for (Pp2mpUnitPortPart candidate : ports) {
            if (!(candidate instanceof Pp2mpUnitCollectPortPart port)) {
                continue;
            }
            int cooldown = this.collectionCooldowns.getOrDefault(port, 0) - ticksSinceLastCall;
            if (cooldown > 0) {
                this.collectionCooldowns.put(port, cooldown);
                continue;
            }

            changed |= port.collectProducts(quantity);
            this.collectionCooldowns.put(
                    port,
                    Math.max(1, this.data.getConfig().general().extractionParam().getInterval())
            );
        }
        return changed;
    }

    /* 仅在拓扑快照变化时核对冷却；稳定期间直接复用网格中的端口列表。 */
    private List<Pp2mpUnitPortPart> activePorts(
            UnitPortType type, Map<? extends Pp2mpUnitPortPart, Integer> cooldowns
    ) {
        IGrid grid = this.mainNode.getGrid();
        List<Pp2mpUnitPortPart> ports = grid == null ? List.of() : Pp2mpGridService.get(grid).getPorts(
                this.part.getFrequency(), this.part.getUnitId(), type);
        List<Pp2mpUnitPortPart> previous = this.cooldownPorts.put(type, ports);
        if (ports.isEmpty()) {
            cooldowns.clear();
        } else if (previous != ports && !cooldowns.isEmpty()) {
            Set<Pp2mpUnitPortPart> active = Collections.newSetFromMap(new IdentityHashMap<>());
            active.addAll(ports);
            cooldowns.keySet().removeIf(port -> !active.contains(port));
        }
        return ports;
    }
}
