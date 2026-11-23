package org.ae2bc.part.input;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.MachineSource;
import net.minecraft.world.level.Level;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.dispatch.RoundRobinPolicy;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskEndpointSelector;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.part.task.Pp2mpTaskEndpoint;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpReceiveTarget;
import org.ae2bc.part.general.Pp2mpTickWake;
import org.ae2bc.pattern.MaterialOutputModeResolver;
import org.ae2bc.pattern.Pp2mpTaskBatch;
import org.ae2bc.pattern.Pp2mpTaskPlan;
import org.ae2bc.storage.ReceiveInventory;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Pp2mpInputLogic {
    private static final int MAX_BATCH_ATTEMPTS_PER_TICK = 64;

    private final IManagedGridNode mainNode;
    private final Pp2mpInputPart part;
    private final IActionSource actionSource;
    private final ReceiveInventory recoveryInventory;
    private final Pp2mpTickWake tickWake = new Pp2mpTickWake();
    private boolean dispatching;
    private long lastDispatchTick = Long.MIN_VALUE;
    private int batchScanCursor;
    private @Nullable AEItemKey compiledDefinition;
    private @Nullable Level compiledLevel;
    private @Nullable IPatternDetails compiledPattern;
    private @Nullable Pp2mpTaskPlan.InputTemplate compiledInputs;

    public Pp2mpInputLogic(Pp2mpInputPart part, IManagedGridNode mainNode) {
        this.part = part;
        this.mainNode = mainNode;
        this.actionSource = new MachineSource(mainNode::getNode);
        Pp2mpReceiveTarget receiveTarget = new Pp2mpReceiveTarget(mainNode::getGrid, this.part::getFrequency);
        this.recoveryInventory = new ReceiveInventory(
                receiveTarget::findInventory, receiveTarget::findStrategies,
                key -> true, (key, amount) -> {
        }, this.actionSource, this::wakeBatchDispatcher);
    }

    public boolean canAcceptPlans() {
        if (this.part.getData().hasBatchTask() || !this.part.getData().getInputRecovery().isEmpty()) {
            return false;
        }
        if (!this.mainNode.isActive() || this.part.noFrequency()) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug(
                        "[P2MP Task] acceptsPlans=false active={} frequency={}",
                        this.mainNode.isActive(),
                        this.part.getFrequency()
                );
            }
            return false;
        }

        return this.hasAvailableEndpoint();
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (this.part.getData().hasBatchTask() || !this.part.getData().getInputRecovery().isEmpty()) {
            return false;
        }
        if (pattern == null || inputs == null) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug("[P2MP Task] input rejected: patternOrInputsNull pattern={} inputs={}", pattern != null, inputs != null);
            }
            return false;
        }

        if (!this.mainNode.isActive() || this.part.noFrequency()) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug(
                        "[P2MP Task] input rejected: inactive={} frequency={}",
                        !this.mainNode.isActive(),
                        this.part.getFrequency()
                );
            }
            return false;
        }

        if (TaskSplitMode.BATCH.equals(this.part.getData().getConfig().taskSplitMode())) {
            Level level = this.part.getLevel();
            if (level == null) {
                return false;
            }
            Pp2mpTaskPlan plan;
            try {
                plan = Pp2mpTaskPlan.resolve(pattern, inputs, level);
            } catch (RuntimeException exception) {
                if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                    Ae2bcMod.LOGGER.debug("[P2MP Batch] unsupported batch: {}", exception.getMessage());
                }
                return false;
            }
            if (!this.hasAvailableEndpoint()) {
                return false;
            }
            this.part.getData().setBatchTask(plan.pattern(), plan.inputs(), plan.totalUnits(), UUID.randomUUID());
            this.batchScanCursor = 0;
            this.part.getHost().markForSave();
            // 接单不触碰机器，先完成上游所有权交接，再由 tick 派发。
            this.wakeBatchDispatcher();
            return true;
        }

        if (!Pp2mpTaskBatch.hasValidPrimaryOutput(pattern)) {
            return false;
        }
        List<Pp2mpTaskEndpoint> endpoints = this.getAllEndpoints();
        if (endpoints.isEmpty()) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug("[P2MP Task] input rejected: no available endpoint");
            }
            return false;
        }

        TaskAllocationMode allocationMode = this.part.getData().getConfig().taskAllocationMode();
        UUID taskId = UUID.randomUUID();
        int selectedIndex = TaskEndpointSelector.select(
                allocationMode,
                this.part.getData().getRoundRobinCursor(),
                endpoints.size(), bound -> ThreadLocalRandom.current().nextInt(bound),
                index -> {
                    Pp2mpTaskEndpoint endpoint = endpoints.get(index);
                    boolean accepted = this.tryAcceptTaskWithTickets(
                            endpoint, pattern, inputs, taskId
                    );
                    if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                        Ae2bcMod.LOGGER.debug(
                                "[P2MP Task] endpoint attempt index={} type={} id={} accepted={}",
                                index, endpoint.getClass().getSimpleName(), endpoint.getEndpointId(), accepted
                        );
                    }
                    return accepted;
                }
        );
        if (selectedIndex < 0) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug("[P2MP Task] input rejected: all endpoints refused");
            }
            return false;
        }

        int cursor = RoundRobinPolicy.advance(selectedIndex, endpoints.size());
        this.part.getData().setRoundRobinCursor(cursor);
        this.part.getHost().markForSave();
        if (Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Task] input accepted endpointIndex={} endpointCount={}",
                    selectedIndex,
                    endpoints.size()
            );
        }
        return true;
    }

    public void resetTaskState() {
        Pp2mpInputData data = this.part.getData();
        if (data.hasBatchTask()) {
            data.setBatchCancelRequested(true);
            if (data.getBatchInFlightEndpointId() == null) {
                data.recoverBatchInputs();
            }
        }
        this.part.getHost().markForSave();
        this.wakeBatchDispatcher();
        IGrid grid = this.mainNode.getGrid();
        if (grid == null || this.part.noFrequency()) {
            return;
        }

        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        short frequency = this.part.getFrequency();
        gridService.getOutputs(frequency).forEach(output -> output.getLogic().resetTaskState());
        gridService.getManagers(frequency).forEach(manager -> manager.getLogic().resetTaskState());
    }

    /* 查询拆除掉落的真实余量；目标暂不可查询时不复制在途批次。 */
    public Map<AEKey, Long> storedInputs() {
        Pp2mpInputData data = this.part.getData();
        boolean excludeInFlight = data.getBatchInFlightEndpointId() != null;
        if (excludeInFlight) {
            Pp2mpTaskEndpoint target = Pp2mpInputLogic.findEndpoint(
                    this.getAllEndpoints(), data.getBatchInFlightEndpointId());
            excludeInFlight = target == null || target.hasAcceptedTask(this.batchTaskId(data.getBatchInFlightCursor()));
        }
        return data.storedInputs(excludeInFlight);
    }

    /* 每 tick 有界派发，禁止回传产物引发的重入重复领取同一批。 */
    public boolean dispatchBatchTask() {
        Level level = this.part.getLevel();
        if (this.dispatching || level == null) {
            return false;
        }
        long tick = level.getGameTime();
        if (this.lastDispatchTick == tick) {
            return false;
        }
        this.lastDispatchTick = tick;
        this.dispatching = true;
        try {
            return this.dispatchAvailableBatches();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            // 存档或解析异常时保留已持有材料，不能通过清空任务“恢复”。
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug("[P2MP Batch] dispatch paused: {}", exception.getMessage());
            }
            return false;
        } finally {
            this.dispatching = false;
        }
    }

    /* 每个空闲端点只领取一批，不再按端点数量分摊全部剩余材料。 */
    private boolean dispatchAvailableBatches() {
        Pp2mpInputData data = this.part.getData();
        if (!data.hasBatchTask() || !this.mainNode.isActive() || this.part.noFrequency()) {
            return false;
        }
        IGrid grid = this.mainNode.getGrid();
        Level level = this.part.getLevel();
        if (grid == null || level == null) {
            return false;
        }
        List<Pp2mpTaskEndpoint> allEndpoints = this.getAllEndpoints();
        AEItemKey batchPattern = data.getBatchPattern();
        if (!data.hasBatchTask() || batchPattern == null) {
            return false;
        }
        IPatternDetails pattern = this.compiledPattern(batchPattern, level);
        Pp2mpTaskPlan.InputTemplate template = this.compiledInputs;
        if (template == null) {
            throw new IllegalStateException("Missing compiled batch inputs");
        }
        boolean changed = false;
        if (data.getBatchInFlightEndpointId() != null) {
            Pp2mpTaskEndpoint target = Pp2mpInputLogic.findEndpoint(
                    allEndpoints, data.getBatchInFlightEndpointId());
            if (target == null) {
                return false;
            }
            UUID taskId = this.batchTaskId(data.getBatchInFlightCursor());
            if (data.isBatchInFlightCommitted()) {
                this.finishBatchAcknowledgement(target, taskId);
                changed = true;
            } else if (target.hasAcceptedTask(taskId)) {
                this.commitBatchDispatch(template,
                        target, taskId, allEndpoints.indexOf(target), allEndpoints.size());
                changed = true;
            } else if (data.isBatchCancelRequested()) {
                data.clearBatchInFlight();
            } else {
                KeyCounter[] offered = template.offer(data.getBatchInputs());
                if (this.tryDispatchBatch(pattern, target, taskId, offered)) {
                    this.commitBatchDispatch(template, target, taskId, allEndpoints.indexOf(target), allEndpoints.size());
                    changed = true;
                } else {
                    data.clearBatchInFlight();
                }
            }
            this.part.getHost().markForSave();
        }
        if (data.isBatchCancelRequested()) {
            data.recoverBatchInputs();
            this.part.getHost().markForSave();
            return true;
        }
        if (!data.hasBatchTask() || allEndpoints.isEmpty()) {
            return changed;
        }
        int start = switch (data.getConfig().taskAllocationMode()) {
            case ROUND_ROBIN -> Math.floorMod(data.getRoundRobinCursor(), allEndpoints.size());
            case RANDOM -> ThreadLocalRandom.current().nextInt(allEndpoints.size());
            case PRIORITY -> Math.floorMod(this.batchScanCursor, allEndpoints.size());
        };
        int attempts = Math.min(allEndpoints.size(), Pp2mpInputLogic.MAX_BATCH_ATTEMPTS_PER_TICK);
        for (int offset = 0; offset < attempts && data.hasBatchTask(); offset++) {
            int index = (start + offset) % allEndpoints.size();
            this.batchScanCursor = (index + 1) % allEndpoints.size();
            Pp2mpTaskEndpoint endpoint = allEndpoints.get(index);
            if (!endpoint.isOperational() || !endpoint.canAcceptTask()) {
                continue;
            }
            String endpointId = endpoint.getEndpointId();
            if (endpointId == null) {
                continue;
            }
            data.setBatchInFlight(endpointId, 1, data.getBatchCursor());
            this.part.getHost().markForSave();
            UUID taskId = this.batchTaskId(data.getBatchCursor());
            KeyCounter[] offered = template.offer(data.getBatchInputs());
            if (!this.tryDispatchBatch(pattern, endpoint, taskId, offered)) {
                data.clearBatchInFlight();
                this.part.getHost().markForSave();
                continue;
            }
            this.commitBatchDispatch(template, endpoint, taskId, index, allEndpoints.size());
            changed = true;
        }
        if (attempts == allEndpoints.size()) {
            this.batchScanCursor = 0;
        } else if (data.getConfig().taskAllocationMode() == TaskAllocationMode.ROUND_ROBIN) {
            data.setRoundRobinCursor(this.batchScanCursor);
        }
        return changed;
    }

    /* 仅精确匹配新版端点身份；冲突或离网时等待，不猜测交付归属。 */
    static @Nullable Pp2mpTaskEndpoint findEndpoint(
            List<Pp2mpTaskEndpoint> endpoints, @Nullable String endpointId
    ) {
        if (endpointId == null || !endpointId.startsWith("v2|")) {
            return null;
        }
        Pp2mpTaskEndpoint result = null;
        for (Pp2mpTaskEndpoint endpoint : endpoints) {
            if (endpointId.equals(endpoint.getEndpointId())) {
                if (result != null) {
                    return null;
                }
                result = endpoint;
            }
        }
        return result;
    }

    private UUID batchTaskId(long cursor) {
        return UUID.nameUUIDFromBytes(
                (this.part.getData().getBatchRootId() + ":" + cursor).getBytes(StandardCharsets.UTF_8));
    }

    private boolean tryDispatchBatch(
            IPatternDetails pattern, Pp2mpTaskEndpoint endpoint, UUID taskId, KeyCounter[] inputs
    ) {
        if (endpoint.hasAcceptedTask(taskId)) {
            return true;
        }
        boolean accepted = this.tryAcceptTaskWithTickets(endpoint, pattern, inputs, taskId);
        if (accepted) {
            endpoint.holdBatchReceipt(taskId);
        }
        return accepted;
    }

    /* 先确认重试回执，再接收假合成票据；拒单时撤销本次暂存。 */
    private boolean tryAcceptTaskWithTickets(
            Pp2mpTaskEndpoint endpoint, IPatternDetails pattern,
            KeyCounter[] inputs, UUID taskId
    ) {
        if (endpoint.hasAcceptedTask(taskId)) {
            return true;
        }
        if (!MaterialOutputModeResolver.hasFakeInputs(pattern)) {
            return endpoint.tryAcceptTask(pattern, inputs, this.actionSource, taskId);
        }
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        // 重载或改频后仍先寻找原归属，不能在另一台转换器重复接收票据。
        FakeCraftingConverterBlockEntity owner = FakeCraftingConverterBlockEntity.findTaskOwner(grid, taskId);
        if (owner != null) {
            boolean accepted = endpoint.tryAcceptTask(pattern, inputs, this.actionSource, taskId);
            if (!accepted) {
                owner.cancelTaskTickets(taskId, false);
            }
            return accepted;
        }
        if (!endpoint.isOperational() || !endpoint.canAcceptTask()) {
            return false;
        }
        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        FakeCraftingConverterBlockEntity converter = gridService.findFakeCraftingConverter(this.part.getFrequency());
        if (converter == null || !converter.getMainNode().isActive()) {
            return false;
        }
        FakeCraftingConverterBlockEntity.TicketPlan ticketPlan = converter.planTask(pattern, inputs);
        if (!ticketPlan.valid() || !ticketPlan.hasTickets() || !converter.acceptTaskInputs(taskId, ticketPlan)) {
            return false;
        }
        boolean accepted = endpoint.tryAcceptTask(
                pattern, ticketPlan.forwardedInputs(), this.actionSource, taskId
        );
        if (!accepted) {
            converter.cancelTaskTickets(taskId, false);
        }
        return accepted;
    }

    private void commitBatchDispatch(
            Pp2mpTaskPlan.InputTemplate inputs, Pp2mpTaskEndpoint endpoint, UUID taskId, int index, int endpointCount
    ) {
        Pp2mpInputData data = this.part.getData();
        data.commitBatchInputs(inputs);
        data.setRoundRobinCursor(index < 0 ? 0 : (index + 1) % Math.max(1, endpointCount));
        this.part.getHost().markForSave();
        this.finishBatchAcknowledgement(endpoint, taskId);
    }

    private void finishBatchAcknowledgement(Pp2mpTaskEndpoint endpoint, UUID taskId) {
        Pp2mpInputData data = this.part.getData();
        endpoint.acknowledgeBatch(taskId);
        data.clearBatchInFlight();
        if (data.getBatchRemainingUnits() == 0) {
            data.clearBatchTask();
            this.compiledDefinition = null;
            this.compiledPattern = null;
            this.compiledLevel = null;
            this.compiledInputs = null;
        }
        this.part.getHost().markForSave();
    }

    /* 恢复材料先完成本地扣账，再允许供应器立即回传网络。 */
    public boolean drainInputRecovery() {
        if (!this.mainNode.isActive()) {
            return false;
        }
        return this.recoveryInventory.drainUntracked(
                this.part.getData().getInputRecovery(), this.part.getHost()::markForSave);
    }

    public void wakeBatchDispatcher() {
        this.tickWake.alert(this.mainNode);
    }

    void resetTickWake() {
        this.tickWake.reset();
    }

    private List<Pp2mpTaskEndpoint> getAllEndpoints() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null || this.part.noFrequency()) {
            return List.of();
        }
        return Pp2mpGridService.get(grid).getEndpoints(this.part.getFrequency());
    }

    private boolean hasAvailableEndpoint() {
        for (Pp2mpTaskEndpoint endpoint : this.getAllEndpoints()) {
            if (endpoint.isOperational() && endpoint.canAcceptTask()) {
                return true;
            }
        }
        return false;
    }

    /* 单个批次任务复用解析结果，切换样板或世界时重新编译。 */
    private IPatternDetails compiledPattern(AEItemKey definition, Level level) {
        if (this.compiledPattern == null || !definition.equals(this.compiledDefinition) || this.compiledLevel != level) {
            this.compiledPattern = Pp2mpTaskPlan.restore(definition, level);
            this.compiledDefinition = definition;
            this.compiledLevel = level;
            this.compiledInputs = new Pp2mpTaskPlan.InputTemplate(this.compiledPattern);
        }
        return this.compiledPattern;
    }
}
