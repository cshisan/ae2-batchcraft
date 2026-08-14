package cn.ae2bc.performance;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.server.testplots.TestPlots;
import appeng.server.testworld.PlotBuilder;
import cn.ae2bc.logic.ProductExtractionBudget;
import cn.ae2bc.logic.ProductExtractionGridService;
import cn.ae2bc.logic.ProductExtractionTask;
import cn.ae2bc.logic.ProductExtractionTickState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@GameTestHolder("ae2_batchcraft")
public final class SchedulingAcceptanceGameTest {
    static {
        registerPlot();
    }

    private SchedulingAcceptanceGameTest() {
    }

    @GameTest(template = "scheduling_acceptance", batch = "ae2bc_2000", timeoutTicks = 40)
    public static void extractionBudgetFairness(GameTestHelper helper) {
        IGrid grid = proxy(IGrid.class, null);
        IGridNode node = proxy(IGridNode.class, grid);
        ProductExtractionGridService service = new ProductExtractionGridService(grid);
        List<BudgetTask> tasks = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            BudgetTask task = new BudgetTask();
            tasks.add(task);
            service.wake(node, task);
        }

        for (int tick = 1; tick <= 12; tick++) {
            for (BudgetTask task : tasks) {
                task.currentTick = tick;
            }
            service.onServerStartTick();
        }

        int minimumRuns = tasks.stream().mapToInt(task -> task.runs).min().orElseThrow();
        int maximumRuns = tasks.stream().mapToInt(task -> task.runs).max().orElseThrow();
        int maximumWait = tasks.stream().mapToInt(task -> task.maximumWait).max().orElseThrow();
        helper.assertTrue(minimumRuns >= 10, "Every endpoint must continue to receive grid budget");
        helper.assertTrue(maximumRuns - minimumRuns <= 1, "Grid budget must rotate fairly");
        helper.assertTrue(maximumWait <= 2, "A runnable endpoint must not wait more than two ticks");
        helper.succeed();
    }

    @GameTest(template = "scheduling_acceptance", batch = "ae2bc_2001", timeoutTicks = 40)
    public static void extractionBackoffAndWake(GameTestHelper helper) {
        IGrid grid = proxy(IGrid.class, null);
        IGridNode node = proxy(IGridNode.class, grid);
        ProductExtractionGridService service = new ProductExtractionGridService(grid);
        NoProgressTask task = new NoProgressTask();
        service.wake(node, task);

        for (int tick = 1; tick <= 8; tick++) {
            task.currentTick = tick;
            service.onServerStartTick();
        }
        helper.assertTrue(task.runTicks.equals(List.of(1, 2, 4, 8)),
                "Repeated no-progress polling did not use bounded exponential backoff: " + task.runTicks);

        service.wake(node, task);
        task.currentTick = 9;
        service.onServerStartTick();
        helper.assertTrue(task.runTicks.equals(List.of(1, 2, 4, 8, 9)),
                "Target-change wake did not override the old retry deadline: " + task.runTicks);
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, IGrid grid) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> {
                    if (method.getName().equals("getGrid")) {
                        return grid;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private static void registerPlot() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "ae2_batchcraft", "schedulingacceptancegametest.scheduling_acceptance");
        try {
            TestPlots.getPlotIds();
            Field plotsField = TestPlots.class.getDeclaredField("plots");
            plotsField.setAccessible(true);
            Map<ResourceLocation, Consumer<PlotBuilder>> plots =
                    (Map<ResourceLocation, Consumer<PlotBuilder>>) plotsField.get(null);
            plots.put(id, plot -> plot.cable("0 0 0"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register scheduling acceptance plot", e);
        }
    }

    private static final class BudgetTask implements ProductExtractionTask {
        private int currentTick;
        private int previousRunTick;
        private int maximumWait;
        private int runs;

        @Override
        public boolean hasProductExtractionWork() {
            return true;
        }

        @Override
        public int getProductExtractionInterval() {
            return 1;
        }

        @Override
        public ProductExtractionTickState tickProductExtraction() {
            throw new AssertionError("Budget-aware overload must be used");
        }

        @Override
        public ProductExtractionTickState tickProductExtraction(ProductExtractionBudget budget) {
            if (previousRunTick != 0) {
                maximumWait = Math.max(maximumWait, currentTick - previousRunTick);
            }
            previousRunTick = currentTick;
            runs++;
            if (!budget.consumeSuccessfulRound()) {
                throw new AssertionError("Endpoint was invoked without extraction budget");
            }
            return ProductExtractionTickState.BUDGET_EXHAUSTED;
        }
    }

    private static final class NoProgressTask implements ProductExtractionTask {
        private final List<Integer> runTicks = new ArrayList<>();
        private int currentTick;

        @Override
        public boolean hasProductExtractionWork() {
            return true;
        }

        @Override
        public int getProductExtractionInterval() {
            return 1;
        }

        @Override
        public ProductExtractionTickState tickProductExtraction() {
            runTicks.add(currentTick);
            return ProductExtractionTickState.NO_PROGRESS;
        }
    }
}
