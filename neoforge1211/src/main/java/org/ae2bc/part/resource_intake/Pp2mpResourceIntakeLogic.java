package org.ae2bc.part.resource_intake;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.helpers.MachineSource;
import net.minecraft.server.level.ServerLevel;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.common.Utils;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.general.Pp2mpResourceIntakeTarget;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源接收端调度逻辑。来源资源只有在目标端预检可接收后才会被提取，余量缓存仅保存实际插入不足的部分。
 */
public final class Pp2mpResourceIntakeLogic {
    private static final int MAX_PULL_ENTRIES_PER_CYCLE = 64;
    private static final int MAX_DISTRIBUTION_ENTRIES_PER_TICK = 64;
    private static final int MAX_TARGET_VISITS = 256;
    private static final int SNAPSHOT_MAX_AGE = 200;

    private final Pp2mpResourceIntakePart part;
    private final GenericStackInv buffer;
    private final Pp2mpResourceIntakeTarget sourceTarget;
    private final MachineSource actionSource;
    private final Pp2mpResourceIntakeStorage intakeStorage;
    private final Pp2mpResourceIntakeEnergy energy;
    private int outputCursor;
    private int pullCooldown;
    private int pendingCursor;
    private List<AEKey> pullCandidates = List.of();
    private int pullCursor;
    private int snapshotAge;
    private int snapshotVersion;
    private long snapshotGeneration = Long.MIN_VALUE;
    private boolean candidatesExhausted = true;
    private boolean transferring;

    public Pp2mpResourceIntakeLogic(Pp2mpResourceIntakePart part, GenericStackInv buffer) {
        this.part = part;
        this.buffer = buffer;
        this.actionSource = new MachineSource(part.getMainNode()::getNode);
        this.intakeStorage = new Pp2mpResourceIntakeStorage(this);
        this.energy = new Pp2mpResourceIntakeEnergy(part.getMainNode()::getGrid);
        Pp2mpAdjacentTarget adjacent = Pp2mpAdjacentTarget.forPart(part);
        this.sourceTarget = new Pp2mpResourceIntakeTarget(
                adjacent, this::onSourceContentsChanged, this::onSourceChanged);
    }

    /* 每 tick 优先重试真实插入不足的余量，再从来源直接分配资源。 */
    public boolean tick(int ticksSinceLastCall) {
        if (this.transferring || !(this.part.getLevel() instanceof ServerLevel)
                || this.part.getMainNode().getGrid() == null) {
            return false;
        }
        this.transferring = true;
        try {
            this.snapshotAge = (int) Math.min(Pp2mpResourceIntakeLogic.SNAPSHOT_MAX_AGE,
                    (long) this.snapshotAge + Math.max(0, ticksSinceLastCall));
            boolean activePull = this.part.getData().isActivePull();
            this.pullCooldown = activePull ? this.pullCooldown - ticksSinceLastCall : 0;
            boolean pullDue = activePull && this.pullCooldown <= 0;
            WorkBudget budget = new WorkBudget(Pp2mpResourceIntakeLogic.MAX_TARGET_VISITS);
            Destinations destinations = this.findDestinations();
            // 余量优先，但为主动提取预留额度，避免满目标长期耗尽全部预算。
            boolean changed = this.flushPending(destinations, pullDue
                    ? budget.limitedTo(Pp2mpResourceIntakeLogic.MAX_TARGET_VISITS / 2) : budget);
            if (pullDue) {
                changed |= this.pullFromSource(this.part.getData().getExtractionParam().getQuantity(), destinations, budget);
                this.pullCooldown = Math.max(1, this.part.getData().getExtractionParam().getInterval());
            }
            return changed;
        } finally {
            this.transferring = false;
        }
    }

    /* 配置改变后清除主动提取冷却，使新参数立即生效。 */
    public void resetPullCooldown() {
        this.pullCooldown = 0;
    }

    /* 库存收发或能力变化只标记候选过期，不在传输回调中改动正在遍历的列表。 */
    public void onSourceChanged() {
        this.candidatesExhausted = true;
        this.snapshotAge = Pp2mpResourceIntakeLogic.SNAPSHOT_MAX_AGE;
        this.part.wake();
    }

    /* 自身收发只改变数量，不废弃键快照；能力失效仍通过独立回调刷新。 */
    private void onSourceContentsChanged() {
        if (!this.transferring) {
            this.onSourceChanged();
        }
    }

    /* 部件离开世界时释放来源能力和候选快照。 */
    public void clearTarget() {
        this.sourceTarget.clear();
        this.resetCandidates();
    }

    private void resetCandidates() {
        this.pullCandidates = List.of();
        this.pullCursor = 0;
        this.snapshotGeneration = Long.MIN_VALUE;
        this.candidatesExhausted = true;
    }

    public boolean hasResourceTargets() {
        return !this.findDestinations().isEmpty()
                || AEKeyTypes.getAll().stream().anyMatch(this.energy::supports);
    }

    public boolean supportsResourceType(AEKeyType type) {
        return this.energy.supports(type) || !this.findDestinations().isEmpty();
    }

    public Pp2mpResourceIntakeStorage getResourceIntakeStorage() {
        return this.intakeStorage;
    }

    /* 模拟资源接收端即时转发，调用方据此决定是否从来源提取或接受资源。 */
    public long simulateIntake(AEKey key, long amount) {
        return this.intake(key, amount, false);
    }

    /* 将资源立即分配给目标端，不把正常供应资源写入余量缓存。 */
    public long insertIntake(AEKey key, long amount) {
        return this.intake(key, amount, true);
    }

    /* 外部模拟和插入独立规划；拒收部分仍归调用方，禁止递归转发复制资源。 */
    private long intake(AEKey key, long amount, boolean execute) {
        if (this.transferring || key == null || amount <= 0
                || !this.part.getData().getProviderFilter().allows(key)) {
            return 0;
        }
        this.transferring = true;
        try {
            ResourceTransferPlan plan = this.plan(key, amount, this.findDestinations(),
                    new WorkBudget(Pp2mpResourceIntakeLogic.MAX_TARGET_VISITS), execute);
            return execute ? plan.execute(plan.amount()) : plan.amount();
        } finally {
            this.transferring = false;
        }
    }

    private boolean isOperational() {
        return this.part.getMainNode().isActive()
                && this.part.getFrequency() != 0
                && this.part.getMainNode().getGrid() != null;
    }

    /* 只提取有执行额度的计划资源，差额缓存仅保存实际接受量下降产生的余量。 */
    private boolean pullFromSource(int maxOperations, Destinations destinations, WorkBudget budget) {
        if (maxOperations <= 0 || budget.remaining() == 0) {
            return false;
        }
        this.refreshCandidates();
        int attempts = Math.min(Pp2mpResourceIntakeLogic.MAX_PULL_ENTRIES_PER_CYCLE, this.pullCandidates.size());
        Map<AEKeyType, MEStorage> sources = new IdentityHashMap<>();
        int movedOperations = 0;
        for (int visited = 0; visited < attempts && budget.remaining() > 0
                && movedOperations < maxOperations; visited++) {
            if (this.sourceTarget.generation() != this.snapshotGeneration) {
                this.resetCandidates();
                break;
            }
            AEKey key = this.pullCandidates.get(this.pullCursor);
            this.pullCursor = (this.pullCursor + 1) % this.pullCandidates.size();
            this.candidatesExhausted |= this.pullCursor == 0;
            if (!this.part.getData().getProviderFilter().allows(key)
                    || (destinations.isEmpty() && !this.energy.supports(key.getType()))) {
                continue;
            }
            if (!budget.tryUse()) {
                break;
            }
            if (!sources.containsKey(key.getType())) {
                sources.put(key.getType(), this.sourceTarget.findSource(key.getType()));
            }
            MEStorage source = sources.get(key.getType());
            if (source == null) {
                continue;
            }
            long requested = Utils.saturatingMultiply(
                    maxOperations - movedOperations, Math.max(1L, key.getAmountPerOperation()));
            long available = Math.clamp(source.extract(key, requested, Actionable.SIMULATE, this.actionSource),
                    0, requested);
            long safeAmount = this.simulateBufferInsert(key, available);
            ResourceTransferPlan plan = this.plan(key, safeAmount, destinations, budget, true);
            if (plan.amount() <= 0) {
                continue;
            }
            long extracted = Math.clamp(source.extract(
                    key, plan.amount(), Actionable.MODULATE, this.actionSource), 0, plan.amount());
            long inserted = plan.execute(extracted);
            if (extracted > inserted) {
                this.storePendingOrRestore(source, key, extracted - inserted);
            }
            movedOperations = (int) Math.min(maxOperations, (long) movedOperations
                    + Utils.operationsFor(extracted, Math.max(1L, key.getAmountPerOperation())));
        }
        return movedOperations > 0;
    }

    /* 白名单直接使用标记键；其他模式分段轮询键快照，数量始终从来源重新模拟。 */
    private void refreshCandidates() {
        Pp2mpProviderFilterData filter = this.part.getData().getProviderFilter();
        long generation = this.sourceTarget.generation();
        boolean invalid = generation != this.snapshotGeneration || filter.version() != this.snapshotVersion;
        boolean whitelist = filter.isWhitelist() && !filter.markers().isEmpty();
        if (!invalid && (whitelist || !this.candidatesExhausted
                && this.snapshotAge < Pp2mpResourceIntakeLogic.SNAPSHOT_MAX_AGE)) {
            return;
        }
        Set<AEKey> keys = new LinkedHashSet<>();
        if (whitelist) {
            for (int slot = 0; slot < filter.markers().size(); slot++) {
                AEKey key = filter.markers().getKey(slot);
                if (key != null) {
                    keys.add(key);
                }
            }
        } else {
            Map<AEKeyType, MEStorage> sources = this.sourceTarget.findSources();
            Set<MEStorage> uniqueSources = Collections.newSetFromMap(new IdentityHashMap<>());
            uniqueSources.addAll(sources.values());
            for (MEStorage source : uniqueSources) {
                for (Map.Entry<AEKey, Long> entry : source.getAvailableStacks()) {
                    if (entry.getValue() > 0 && sources.get(entry.getKey().getType()) == source) {
                        keys.add(entry.getKey());
                    }
                }
            }
        }
        this.pullCandidates = List.copyOf(keys);
        this.pullCursor = invalid || keys.isEmpty() ? 0 : Math.floorMod(this.pullCursor, keys.size());
        this.snapshotGeneration = generation;
        this.snapshotVersion = filter.version();
        this.snapshotAge = 0;
        this.candidatesExhausted = false;
    }

    /* 余量按槽位轮询，失败访问也计入预算。 */
    private boolean flushPending(Destinations destinations, WorkBudget budget) {
        if (this.buffer.isEmpty()) {
            return false;
        }
        boolean changed = false;
        int attempts = Math.min(this.buffer.size(), Pp2mpResourceIntakeLogic.MAX_DISTRIBUTION_ENTRIES_PER_TICK);
        for (int visited = 0; visited < attempts && budget.remaining() > 0; visited++) {
            int slot = Math.floorMod(this.pendingCursor++, this.buffer.size());
            GenericStack stack = this.buffer.getStack(slot);
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            ResourceTransferPlan plan = this.plan(stack.what(), stack.amount(), destinations, budget, true);
            long inserted = plan.execute(plan.amount());
            if (inserted > 0) {
                this.buffer.extract(slot, stack.what(), inserted, Actionable.MODULATE);
                changed = true;
            }
        }
        return changed;
    }

    private Destinations findDestinations() {
        IGrid grid = this.part.getMainNode().getGrid();
        if (grid == null || !this.isOperational()) {
            return new Destinations(List.of(), List.of());
        }
        Pp2mpGridService gridService = Pp2mpGridService.get(grid);
        return new Destinations(gridService.getOutputs(this.part.getFrequency()),
                gridService.getManagers(this.part.getFrequency()));
    }

    /* 供电优先，目标访问先计入预算；执行阶段不再因预算不足中途截断。 */
    private ResourceTransferPlan plan(
            AEKey key, long amount, Destinations destinations, WorkBudget budget, boolean advance
    ) {
        if (amount <= 0 || !budget.tryUse()) {
            return ResourceTransferPlan.empty();
        }
        long networkAmount = this.energy.insert(key, amount, Actionable.SIMULATE);
        ResourceTransferPlan network = ResourceTransferPlan.leaf(
                networkAmount, supplied -> this.energy.insert(key, supplied, Actionable.MODULATE));
        ResourceTransferPlan distribution = this.planDistribution(
                key, amount - networkAmount, destinations, budget, advance);
        return ResourceTransferPlan.combine(amount, List.of(network, distribution), false);
    }

    private ResourceTransferPlan planDistribution(
            AEKey key, long amount, Destinations destinations, WorkBudget budget, boolean advance
    ) {
        if (amount <= 0 || destinations.isEmpty()) {
            return ResourceTransferPlan.empty();
        }
        int mode = this.part.getData().getDistributionMode();
        int start = Math.floorMod(this.outputCursor, destinations.size());
        List<ResourceTransferPlan> plans = new ArrayList<>();
        long remaining = amount;
        int visited = 0;
        while (visited < destinations.size() && budget.remaining() > 0 && (mode == 2 || remaining > 0)) {
            ResourceTransferPlan child = destinations.plan(
                    (start + visited) % destinations.size(), key, mode == 2 ? amount : remaining, budget, advance);
            visited++;
            if (child.amount() > 0) {
                plans.add(child);
                remaining -= Math.min(remaining, child.amount());
            }
        }
        if (advance && visited > 0) {
            boolean incomplete = visited < destinations.size() && budget.remaining() == 0;
            this.outputCursor = mode == 0 || incomplete ? (start + visited) % destinations.size()
                    : mode == 2 ? (start + 1) % destinations.size() : 0;
        }
        return ResourceTransferPlan.combine(amount, plans, mode == 2);
    }

    private long simulateBufferInsert(AEKey key, long amount) {
        long accepted = 0;
        for (int slot = 0; slot < this.buffer.size() && accepted < amount; slot++) {
            if (this.buffer.isAllowedIn(slot, key)) {
                accepted += this.buffer.insert(slot, key, amount - accepted, Actionable.SIMULATE);
            }
        }
        return Math.min(amount, accepted);
    }

    private void storePendingOrRestore(MEStorage source, AEKey key, long amount) {
        long remaining = amount;
        for (int slot = 0; slot < this.buffer.size() && remaining > 0; slot++) {
            if (this.buffer.isAllowedIn(slot, key)) {
                long stored = this.buffer.insert(slot, key, remaining, Actionable.MODULATE);
                remaining -= Math.clamp(stored, 0, remaining);
            }
        }
        if (remaining > 0) {
            long restored = source.insert(key, remaining, Actionable.MODULATE, this.actionSource);
            remaining -= Math.clamp(restored, 0, remaining);
        }
        if (remaining > 0) {
            Ae2bcMod.LOGGER.error(
                    "Resource intake pending insertion recovery failed key={} amount={} at {}",
                    key, remaining, this.part.getBlockEntity().getBlockPos()
            );
        }
    }

    private record Destinations(List<Pp2mpOutputPart> outputs, List<Pp2mpUnitManagerPart> managers) {
        private int size() {
            return this.outputs.size() + this.managers.size();
        }

        private boolean isEmpty() {
            return this.size() == 0;
        }

        private ResourceTransferPlan plan(int index, AEKey key, long amount, WorkBudget budget, boolean advance) {
            if (index < this.outputs.size()) {
                return this.outputs.get(index).getLogic().planProvider(key, amount, budget);
            }
            return this.managers.get(index - this.outputs.size()).getLogic().planProvider(key, amount, budget, advance);
        }
    }
}
