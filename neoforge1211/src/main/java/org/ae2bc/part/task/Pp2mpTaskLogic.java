package org.ae2bc.part.task;

import appeng.api.networking.IGrid;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.parts.AEBasePart;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.pattern.MaterialOutputModeResolver;
import org.ae2bc.pattern.Pp2mpTaskBatch;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.storage.ProductReturnMatcher;
import org.ae2bc.storage.ReceiveInventory;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 端点共用任务计数、回执、恢复和票据结算，各执行器保留自身材料发送方式。
 */
public abstract class Pp2mpTaskLogic implements Pp2mpTaskEndpoint {
    private final AEBasePart owner;
    private final Supplier<@Nullable IGrid> gridSupplier;
    protected final Pp2mpTaskData task;
    private final Supplier<ReceiveInventory> receiveInventory;
    private int returnDepth;
    private boolean finishing;
    private boolean completionWakeRequested;

    protected Pp2mpTaskLogic(
            AEBasePart owner, Supplier<@Nullable IGrid> gridSupplier, Pp2mpTaskData task,
            Supplier<ReceiveInventory> receiveInventory
    ) {
        this.owner = owner;
        this.gridSupplier = gridSupplier;
        this.task = task;
        this.receiveInventory = receiveInventory;
    }

    @Override
    public final boolean hasAcceptedTask(UUID taskId) {
        return taskId != null && (this.task.getBatchReceipt().matches(taskId)
                || this.task.getTasks().containsKey(taskId) || taskId.equals(this.task.getLastTaskId()));
    }

    @Override
    public final void holdBatchReceipt(UUID taskId) {
        this.task.getBatchReceipt().remember(taskId);
        this.owner.getHost().markForSave();
    }

    @Override
    public final void acknowledgeBatch(UUID taskId) {
        this.task.getBatchReceipt().acknowledge(taskId);
        this.owner.getHost().markForSave();
    }

    /* 按任务票据查找转换器，允许端点暂时离网。 */
    protected final @Nullable FakeCraftingConverterBlockEntity findFakeTaskConverter(UUID taskId) {
        IGrid grid = this.gridSupplier.get();
        return grid == null ? null : FakeCraftingConverterBlockEntity.findTaskOwner(grid, taskId);
    }

    /* 接单时依据样板跳过普通任务查询，解析结果仅在本次调用内复用。 */
    protected final @Nullable FakeCraftingConverterBlockEntity findFakeTaskConverter(
            IPatternDetails pattern, UUID taskId
    ) {
        return MaterialOutputModeResolver.hasFakeInputs(pattern) ? this.findFakeTaskConverter(taskId) : null;
    }

    /* 生成包含维度、坐标和朝向的端点身份，避免跨维度坐标冲突。 */
    protected final @Nullable String createEndpointId(String kind, boolean center) {
        BlockEntity blockEntity = this.owner.getBlockEntity();
        if (blockEntity == null) {
            return null;
        }
        Level level = blockEntity.getLevel();
        Direction direction = this.owner.getSide();
        if (level == null) {
            return null;
        }
        String side;
        if (center) {
            side = "center";
        } else if (direction == null) {
            return null;
        } else {
            side = direction.getName();
        }
        return "v2|%s|%s|%s|%s".formatted(
                kind,
                level.dimension().location(),
                blockEntity.getBlockPos().asLong(),
                side
        );
    }

    protected static Set<AEKey> getFakeTaskOutputKeys(
            @Nullable FakeCraftingConverterBlockEntity converter, UUID taskId
    ) {
        if (converter == null) {
            return Set.of();
        }
        Set<AEKey> keys = new HashSet<>();
        for (GenericStack output : converter.getTaskOutputs(taskId)) {
            keys.add(output.what());
        }
        return keys;
    }

    /* 普通任务不查转换器，假合成必须找到接单时的原转换器。 */
    private boolean settleTickets(Pp2mpTaskRecord record) {
        IGrid grid = this.gridSupplier.get();
        if (grid == null) {
            return false;
        }
        UUID taskId = record.getId();
        UUID converterId = record.getConverterId();
        if (converterId == null) {
            return true;
        }
        for (FakeCraftingConverterBlockEntity converter : grid.getMachines(FakeCraftingConverterBlockEntity.class)) {
            if (converterId.equals(converter.getConverterId())) {
                if (this.task.isCancelRequested()) {
                    converter.cancelTaskTickets(taskId);
                    return !converter.hasTaskTickets(taskId);
                }
                return converter.returnTaskOutputs(taskId, this.receiveInventory.get());
            }
        }
        return false;
    }

    protected abstract boolean hasPendingInputs();

    protected abstract void recoverPendingInputs();

    protected abstract ReturnMode returnMode();

    protected abstract void taskChanged();

    protected abstract void taskEnded();

    @Override
    public final boolean isTaskActive() {
        return this.task.isTaskActive();
    }

    public final boolean hasTaskState() {
        return this.task.isTaskActive() || this.hasPendingInputs() || this.task.hasRecovery()
                || this.task.getBatchReceipt().isPending();
    }

    /* 只放开等待产物阶段，材料余量、恢复和未确认交付仍串行。 */
    protected final boolean canStartTask(boolean blocking) {
        return !this.finishing && this.returnDepth == 0 && !this.hasPendingInputs() && !this.task.hasRecovery()
                && !this.task.getBatchReceipt().isPending() && !this.task.isCancelRequested()
                && (!blocking || !this.task.isTaskActive());
    }

    protected final void beginTask(
            Pp2mpTaskBatch.OutputPlan outputs, UUID taskId, @Nullable FakeCraftingConverterBlockEntity converter
    ) {
        this.task.begin(outputs.pending(), outputs.primary(), taskId,
                converter == null ? null : converter.getConverterId());
        this.owner.getHost().markForSave();
        this.taskChanged();
    }

    public final boolean allowsReturned(AEKey returned) {
        if (returned == null || !this.task.isTaskActive() || this.task.isCancelRequested()) {
            return false;
        }
        ReturnMode mode = this.returnMode();
        if (mode == ReturnMode.UNBLOCKED) {
            return true;
        }
        for (Pp2mpTaskRecord record : this.task.getTasks().values()) {
            if (ProductReturnMatcher.find(returned, record.getProgress().declared(), mode) != null) {
                return true;
            }
        }
        return false;
    }

    public final @Nullable Set<AEKey> exactReturnKeys() {
        if (this.returnMode() != ReturnMode.STRICT_FULL) {
            return null;
        }
        if (this.task.getTasks().size() == 1) {
            return this.task.getTasks().values().iterator().next().getProgress().declared().keySet();
        }
        Set<AEKey> keys = new HashSet<>();
        for (Pp2mpTaskRecord record : this.task.getTasks().values()) {
            keys.addAll(record.getProgress().declared().keySet());
        }
        return keys;
    }

    /* 只结算实际回传量，声明的返回范围保持到任务结束。 */
    public final void onReturnedInserted(AEKey returned, long amount) {
        if (!this.task.isTaskActive() || this.task.isCancelRequested() || returned == null || amount <= 0) {
            return;
        }
        ReturnMode mode = this.returnMode();
        long remaining = amount;
        for (Pp2mpTaskRecord record : this.task.getTasks().values()) {
            if (remaining == 0) {
                break;
            }
            AEKey matched = ProductReturnMatcher.find(returned, record.getProgress().remaining(),
                    mode == ReturnMode.UNBLOCKED ? ReturnMode.STRICT_FULL : mode, record.getPrimary());
            if (matched != null) {
                long used = Math.min(remaining, record.getProgress().remaining().getOrDefault(matched, 0L));
                record.getProgress().record(matched, used);
                remaining -= used;
                if (!record.pending()) {
                    this.completionWakeRequested |= this.task.getCompletions().add(record.getId());
                }
            }
        }
        if (remaining != amount) {
            this.owner.getHost().markForSave();
        }
        this.finishTaskIfComplete();
        this.wakeCompletionRetry();
    }

    public final void beginReturn() {
        this.returnDepth++;
    }

    /* 来源扣账及差额恢复完成后，才允许任务结束或接受下一单。 */
    public final void endReturn() {
        if (this.returnDepth <= 0) {
            throw new IllegalStateException("Unbalanced task return scope");
        }
        this.returnDepth--;
        this.finishTaskIfComplete();
        this.wakeCompletionRetry();
    }

    /* 只在达量时唤醒一次；堵塞后的重试保留调度器退避。 */
    private void wakeCompletionRetry() {
        if (this.returnDepth == 0 && this.completionWakeRequested) {
            this.completionWakeRequested = false;
            if (this.task.isTaskActive()) {
                this.taskChanged();
            }
        }
    }

    public final void retainExtracted(GenericStack stack) {
        if (stack.amount() > 0) {
            this.task.getExtractionRecovery().merge(stack.what(), stack.amount(), Math::addExact);
            this.owner.getHost().markForSave();
            this.taskChanged();
        }
    }

    /* 恢复余量属于当前任务，取消后的资源不再推进产物计数。 */
    public final boolean drainTaskRecovery() {
        if (!this.task.hasRecovery() || !this.isOperational()) {
            return false;
        }
        ReceiveInventory inventory = this.receiveInventory.get();
        boolean changed = inventory.drainUntracked(this.task.getInputRecovery(), this.owner.getHost()::markForSave);
        changed |= this.task.isCancelRequested() || !this.task.isTaskActive()
                ? inventory.drainUntracked(this.task.getExtractionRecovery(), this.owner.getHost()::markForSave)
                : inventory.drainRecovery(this.task.getExtractionRecovery(), this.owner.getHost()::markForSave);
        this.finishTaskIfComplete();
        return changed;
    }

    protected final void finishTaskIfComplete() {
        if (this.finishing || this.returnDepth > 0 || !this.task.isTaskActive() || this.task.hasRecovery()) {
            return;
        }
        this.finishing = true;
        try {
            boolean changed = false;
            // 只重试已达量记录，避免每次产物回传都扫描全部在途任务。
            Iterator<UUID> completions = this.task.getCompletions().iterator();
            while (completions.hasNext()) {
                UUID id = completions.next();
                Pp2mpTaskRecord record = this.task.getTasks().get(id);
                if (record == null) {
                    completions.remove();
                    continue;
                }
                if ((record.getId().equals(this.task.getDeliveryId()) && this.hasPendingInputs())
                        || (!this.task.isCancelRequested() && record.pending())) {
                    continue;
                }
                if (this.settleTickets(record)) {
                    completions.remove();
                    this.task.getTasks().remove(id);
                    this.task.rememberCompletion(record.getId());
                    changed = true;
                }
            }
            if (changed) {
                this.task.afterCompletion();
                this.taskEnded();
                this.owner.getHost().markForSave();
                this.taskChanged();
            }
        } finally {
            this.finishing = false;
        }
    }

    public final void retryTaskCompletion() {
        this.finishTaskIfComplete();
    }

    @Override
    public final void resetTaskState() {
        this.recoverPendingInputs();
        if (this.task.isTaskActive()) {
            this.task.setCancelRequested(true);
            for (Pp2mpTaskRecord record : this.task.getTasks().values()) {
                record.getProgress().remaining().clear();
                this.task.getCompletions().add(record.getId());
                if (this.settleTickets(record)) {
                    record.setConverterId(null);
                }
            }
        }
        this.owner.getHost().markForSave();
        this.taskChanged();
        this.finishTaskIfComplete();
    }

    /* 保留输入槽结构，只从交给机器的材料中移除假合成票据。 */
    protected static KeyCounter[] filterFakeTaskInputs(
            @Nullable FakeCraftingConverterBlockEntity converter, KeyCounter[] inputs, UUID taskId
    ) {
        return converter == null ? inputs : converter.filterTaskInputs(taskId, inputs);
    }
}
