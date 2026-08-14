package cn.ae2bc.logic;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Schedules product extraction without changing AE2's native pattern-provider ticker. */
public final class ProductExtractionGridService implements IGridService, IGridServiceProvider {
    private final IGrid grid;
    private final Map<ProductExtractionTask, Job> jobs = new IdentityHashMap<>();
    private final DeadlineScheduler<Job> schedule = new DeadlineScheduler<>();
    private long tick;

    public ProductExtractionGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void onServerStartTick() {
        tick++;
        ProductExtractionBudget budget = new ProductExtractionBudget();
        var dueJobs = schedule.takeDue(tick);
        List<Job> budgetExhaustedJobs = new ArrayList<>();
        int index = 0;
        for (; index < dueJobs.size(); index++) {
            Job job = dueJobs.get(index);
            if (job.node.getGrid() != grid || !job.task.hasProductExtractionWork()) {
                remove(job);
                continue;
            }

            if (budget.isGridExhausted()) {
                break;
            }
            budget.beginEndpoint();
            ProductExtractionTickState state = job.task.tickProductExtraction(budget);
            if (state == ProductExtractionTickState.DISABLED) {
                remove(job);
            } else if (state == ProductExtractionTickState.BUDGET_EXHAUSTED) {
                if (!schedule.isScheduled(job)) {
                    budgetExhaustedJobs.add(job);
                }
            } else if (!schedule.isScheduled(job)) {
                schedule.schedule(job, tick + job.backoff.nextDelay(
                        state, job.task.getProductExtractionInterval()));
            }
        }
        // Jobs that did not get grid budget go first next tick, ahead of endpoints that consumed this tick's budget.
        for (; index < dueJobs.size(); index++) {
            Job job = dueJobs.get(index);
            if (job.node.getGrid() == grid && job.task.hasProductExtractionWork()) {
                schedule.schedule(job, tick + 1);
            } else {
                remove(job);
            }
        }
        for (Job job : budgetExhaustedJobs) {
            if (job.node.getGrid() == grid && job.task.hasProductExtractionWork()
                    && !schedule.isScheduled(job)) {
                schedule.schedule(job, tick + 1);
            } else if (job.node.getGrid() != grid || !job.task.hasProductExtractionWork()) {
                remove(job);
            }
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
    }

    @Override
    public void removeNode(IGridNode node) {
        for (var iterator = jobs.values().iterator(); iterator.hasNext(); ) {
            var job = iterator.next();
            if (job.node == node) {
                iterator.remove();
                schedule.cancel(job);
            }
        }
    }

    public void update(IGridNode node, ProductExtractionTask task) {
        if (node.getGrid() != grid || !task.hasProductExtractionWork()) {
            remove(task);
            return;
        }
        wake(node, task);
    }

    public void wake(IGridNode node, ProductExtractionTask task) {
        if (node.getGrid() != grid || !task.hasProductExtractionWork()) {
            remove(task);
            return;
        }
        Job job = jobs.computeIfAbsent(task, ignored -> new Job(node, task));
        job.backoff.reset();
        schedule.schedule(job, tick);
    }

    private void remove(Job job) {
        jobs.remove(job.task, job);
        schedule.cancel(job);
    }

    private void remove(ProductExtractionTask task) {
        var job = jobs.remove(task);
        if (job != null) {
            schedule.cancel(job);
        }
    }

    private static final class Job {
        private final IGridNode node;
        private final ProductExtractionTask task;
        private final ProductExtractionBackoff backoff = new ProductExtractionBackoff();

        private Job(IGridNode node, ProductExtractionTask task) {
            this.node = node;
            this.task = task;
        }
    }
}
