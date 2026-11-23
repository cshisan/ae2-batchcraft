package org.ae2bc.part.output;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.MachineSource;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.common.Utils;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.part.task.Pp2mpTaskLogic;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpCraftingTarget;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpProviderTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.general.Pp2mpInsertPlan;
import org.ae2bc.pattern.*;
import org.ae2bc.storage.ProductExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Pp2mpOutputLogic extends Pp2mpTaskLogic {
    private final @Getter Pp2mpOutputPart part;
    private final @Getter Pp2mpOutputData data;
    private final IActionSource actionSource;
    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Pp2mpSendTarget sendTarget;
    private final Pp2mpCraftingTarget craftingTarget;
    private final Pp2mpProviderTarget providerTarget;
    private final Map<Direction, Pp2mpSendTarget> directionalTargets = new EnumMap<>(Direction.class);
    private final ProductExtractor.Cursor extractionCursor = new ProductExtractor.Cursor();

    public Pp2mpOutputLogic(Pp2mpOutputPart part, Pp2mpOutputData data) {
        super(part, () -> part.getMainNode().getGrid(), data, part::getReceiveInventory);
        this.part = part;
        this.data = data;
        this.actionSource = new MachineSource(part.getMainNode()::getNode);
        this.adjacentTarget = Pp2mpAdjacentTarget.forPart(this.part);
        this.sendTarget = new Pp2mpSendTarget(this.adjacentTarget, this.part::wakeProductExtractor);
        this.craftingTarget = new Pp2mpCraftingTarget(this.adjacentTarget, this.part::wakeProductExtractor);
        this.providerTarget = new Pp2mpProviderTarget(
                this.sendTarget, this.part::getEffectiveProviderFilter, this::isOperational,
                this.actionSource, () -> {
                });
    }

    /* 只规划和模拟，不修改机器库存；接单后由持久化余量驱动真实发送。 */
    private Map<Direction, Map<AEKey, Long>> planExternalInputs(
            IPatternDetails pattern, KeyCounter[] inputs, IActionSource source, UUID taskId,
            @Nullable FakeCraftingConverterBlockEntity converter
    ) {
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return Map.of();
        }
        Map<Direction, Map<AEKey, Long>> planned = new LinkedHashMap<>();
        if (pattern instanceof Pp2mpPatternDetails details
                && (MaterialOutputModeResolver.attr(pattern).hasExplicitDirections()
                || MaterialOutputModeResolver.hasFakeInputs(pattern))) {
            if (!(details.getDelegate() instanceof AEProcessingPattern processingPattern)) {
                return Map.of();
            }
            List<Pp2mpPatternSlotMapper.SlotInput> mapped = Pp2mpPatternSlotMapper.map(
                    processingPattern, inputs);
            if (mapped == null) {
                return Map.of();
            }
            MaterialOutputAttr config = MaterialOutputModeResolver.attr(details);
            for (Pp2mpPatternSlotMapper.SlotInput input : mapped) {
                if (input.stack().amount() > 0
                        && config.mode(input.slot()) != MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                    planned.computeIfAbsent(config.direction(input.slot()), ignored -> new LinkedHashMap<>())
                            .merge(input.stack().what(), input.stack().amount(), Utils::saturatingAdd);
                }
            }
        } else {
            Map<AEKey, Long> requested = new LinkedHashMap<>();
            if (pattern instanceof Pp2mpPatternDetails) {
                requested.putAll(this.flattenInputs(inputs, converter, taskId));
            } else {
                pattern.pushInputsToExternalInventory(inputs, (key, amount) -> {
                    if (key != null && amount > 0) {
                        requested.merge(key, amount, Utils::saturatingAdd);
                    }
                });
            }
            if (!requested.isEmpty()) {
                planned.put(null, requested);
            }
        }
        Pp2mpInsertPlan capacity = new Pp2mpInsertPlan();
        for (Map.Entry<Direction, Map<AEKey, Long>> entry : planned.entrySet()) {
            if (!this.targetFor(entry.getKey()).canInsertAll(entry.getValue(), source, capacity)) {
                return Map.of();
            }
        }
        return planned;
    }

    /* 展开当前任务分片，不根据完整样板数量重新校验。 */
    private Map<AEKey, Long> flattenInputs(
            KeyCounter[] inputs, @Nullable FakeCraftingConverterBlockEntity converter, UUID taskId
    ) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        KeyCounter[] actualInputs = Pp2mpTaskLogic.filterFakeTaskInputs(converter, inputs, taskId);
        for (KeyCounter counter : actualInputs) {
            if (counter == null) {
                continue;
            }
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (amount > 0) {
                    result.merge(entry.getKey(), amount, Utils::saturatingAdd);
                }
            }
        }
        return result;
    }

    private Pp2mpSendTarget targetFor(Direction direction) {
        return direction == null ? this.sendTarget : this.getDirectionalTarget(direction);
    }

    /* 按真实插入量扣减余量；目标满或失效时保留资源，在后续调度重试。 */
    public boolean drainPendingInputs() {
        if (!this.isOperational() || this.data.getPendingInputs().isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<Map.Entry<Direction, Map<AEKey, Long>>> groups = this.data.getPendingInputs().entrySet().iterator();
        while (groups.hasNext()) {
            Map.Entry<Direction, Map<AEKey, Long>> group = groups.next();
            Map<AEKeyType, MEStorage> storages = this.targetFor(group.getKey()).findStorages(group.getValue().keySet());
            changed |= Pp2mpSendTarget.drainPending(
                    group.getValue(), storages, this.actionSource, this.part.getHost()::markForSave
            );
            if (group.getValue().isEmpty()) {
                groups.remove();
            }
        }
        this.finishTaskIfComplete();
        if (changed && this.data.getPendingInputs().isEmpty()) {
            this.wakeBatchInput();
        }
        return changed;
    }

    private Pp2mpSendTarget getDirectionalTarget(Direction direction) {
        Pp2mpSendTarget target = this.directionalTargets.get(direction);
        if (target != null) {
            return target;
        }
        Pp2mpAdjacentTarget adjacent = Pp2mpAdjacentTarget.forPart(this.part, direction);
        target = new Pp2mpSendTarget(adjacent, this.part::wakeProductExtractor);
        this.directionalTargets.put(direction, target);
        return target;
    }

    public boolean accessReceive(AEKey returned) {
        return this.allowsReturned(returned);
    }

    @Override
    protected ReturnMode returnMode() {
        return this.data.getConfig().general().returnMode();
    }

    @Override
    protected boolean hasPendingInputs() {
        return !this.data.getPendingInputs().isEmpty();
    }

    @Override
    protected void taskChanged() {
        this.part.wakeProductExtractor();
        this.wakeBatchInput();
    }

    @Override
    protected void taskEnded() {
    }

    private Map<AEKeyType, MEStorage> getExternalStorages() {
        if (!(this.part.getLevel() instanceof ServerLevel)) {
            return Map.of();
        }
        return this.sendTarget.findStorages();
    }

    /* 部件移除时释放目标；普通库存变化仅唤醒，身份变化由目标解析器检测。 */
    public void clearTarget() {
        this.adjacentTarget.clear();
        this.sendTarget.clear();
        this.craftingTarget.clear();
        for (Pp2mpSendTarget target : this.directionalTargets.values()) {
            target.clear();
        }
        this.directionalTargets.clear();
    }

    public ProductExtractor.Result extractProducts(int quantity) {
        return ProductExtractor.extract(
                this.getExternalStorages(), this.part.getReceiveInventory(),
                quantity, Integer.MAX_VALUE, this.actionSource, this::accessReceive,
                this::retainExtracted, this.exactReturnKeys(), this.extractionCursor
        );
    }

    /* 规划本次供应，不改变合成任务状态。 */
    public ResourceTransferPlan planProvider(AEKey key, long amount, WorkBudget budget) {
        return this.providerTarget.plan(key, amount, budget);
    }

    @Override
    public @Nullable String getEndpointId() {
        return this.createEndpointId("output", false);
    }

    @Override
    public boolean isOperational() {
        return this.part.validFrequency()
                && this.part.getMainNode().isActive()
                && this.part.getSide() != null
                && this.part.getLevel() instanceof ServerLevel;
    }

    @Override
    public boolean canAcceptTask() {
        if (!this.canStartTask(this.data.getConfig().general().blocking())) {
            return false;
        }
        if (!this.isOperational()) {
            return false;
        }

        if (this.craftingTarget.acceptsPlans()) {
            return true;
        }

        return this.sendTarget.hasStorage();
    }

    @Override
    protected void recoverPendingInputs() {
        for (Map<AEKey, Long> group : this.data.getPendingInputs().values()) {
            group.forEach((key, amount) -> this.data.getInputRecovery().merge(key, amount, Math::addExact));
        }
        this.data.getPendingInputs().clear();
    }

    private void wakeBatchInput() {
        IGrid grid = this.part.getMainNode().getGrid();
        if (grid != null && this.part.getFrequency() != 0) {
            Pp2mpGridService.get(grid).wakeBatchInput(this.part.getFrequency());
        }
    }

    /* 按本次完整样板建立产物计数，再将对应材料交给机器或待发送库存。 */
    @Override
    public boolean tryAcceptTask(
            IPatternDetails pattern,
            KeyCounter[] inputs,
            IActionSource source,
            UUID taskId
    ) {
        if (this.hasAcceptedTask(taskId)) {
            return true;
        }
        if (pattern == null || inputs == null || taskId == null
                || !this.canAcceptTask()) {
            return false;
        }

        Direction outputSide = this.part.getSide();
        if (outputSide == null) {
            return false;
        }
        FakeCraftingConverterBlockEntity converter = this.findFakeTaskConverter(pattern, taskId);
        Pp2mpTaskBatch.OutputPlan outputs = Pp2mpTaskBatch.planOutputs(
                pattern, Pp2mpTaskLogic.getFakeTaskOutputKeys(converter, taskId));
        if (outputs == null) {
            return false;
        }

        Direction ejectionDirection = outputSide.getOpposite();
        boolean accepted;
        if (this.craftingTarget.acceptsPlans()) {
            KeyCounter[] machineInputs = inputs;
            IPatternDetails machinePattern = pattern;
            if (MaterialOutputModeResolver.hasFakeInputs(pattern)) {
                // 机器适配使用内部真实配方，不把独立标记写回对 AE2 声明的样板产物。
                List<GenericStack> realInputs = new ArrayList<>();
                this.flattenInputs(inputs, converter, taskId)
                        .forEach((key, amount) -> realInputs.add(new GenericStack(key, amount)));
                List<GenericStack> realOutputs = new ArrayList<>();
                outputs.pending().forEach((key, amount) -> realOutputs.add(new GenericStack(key, amount)));
                if (realInputs.isEmpty() || realOutputs.isEmpty()) {
                    return false;
                }
                AEItemKey machineDefinition = AEItemKey.of(
                        PatternDetailsHelper.encodeProcessingPattern(realInputs, realOutputs));
                if (machineDefinition == null) {
                    return false;
                }
                machinePattern = new AEProcessingPattern(machineDefinition);
                machineInputs = new KeyCounter[realInputs.size()];
                for (int slot = 0; slot < realInputs.size(); slot++) {
                    machineInputs[slot] = new KeyCounter();
                    machineInputs[slot].add(realInputs.get(slot).what(), realInputs.get(slot).amount());
                }
            }
            accepted = this.craftingTarget.pushPattern(
                    machinePattern, machineInputs, ejectionDirection
            );
        } else {
            Map<Direction, Map<AEKey, Long>> planned = this.planExternalInputs(
                    pattern, inputs, source, taskId, converter);
            if (planned.isEmpty()) {
                return false;
            }
            this.data.getPendingInputs().putAll(planned);
            accepted = true;
        }

        if (accepted) {
            this.beginTask(outputs, taskId, converter);
        }

        return accepted;
    }
}
