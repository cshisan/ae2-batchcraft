package cn.ae2bc.performance;

import appeng.api.parts.PartHelper;
import appeng.me.service.P2PService;
import appeng.server.testplots.TestPlots;
import appeng.server.testworld.PlotBuilder;
import cn.ae2bc.logic.PatternP2PTopologyGridService;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@GameTestHolder("ae2_batchcraft")
public final class TopologyPerformanceGameTest {
    private static final short FREQUENCY = 0x2345;
    private static final int WARMUP_TICKS = 120;
    private static final int SAMPLE_TICKS = 120;
    private static final int INVALIDATION_TICKS = 60;
    private static final Path RESULTS = Path.of("results", "topology-1.21.1.csv");
    private static final Map<Integer, BuiltTopology> BUILT_TOPOLOGIES = new ConcurrentHashMap<>();

    static {
        registerPlot(100, 10, 10);
        registerPlot(500, 25, 20);
        registerPlot(1000, 40, 25);
        registerPlot(32, 8, 4);
    }

    private TopologyPerformanceGameTest() {
    }

    @GameTest(template = "topology_100", batch = "ae2bc_0100", timeoutTicks = 700)
    public static void topology100(GameTestHelper helper) {
        runTopologyBenchmark(helper, 100, 10, 10);
    }

    @GameTest(template = "topology_500", batch = "ae2bc_0500", timeoutTicks = 700)
    public static void topology500(GameTestHelper helper) {
        runTopologyBenchmark(helper, 500, 25, 20);
    }

    @GameTest(template = "topology_1000", batch = "ae2bc_1000", timeoutTicks = 700)
    public static void topology1000(GameTestHelper helper) {
        runTopologyBenchmark(helper, 1000, 40, 25);
    }

    @GameTest(template = "topology_32", batch = "ae2bc_1100", timeoutTicks = 160)
    public static void topologyInvalidatesAfterPartRemovalAndRestoresAfterReplacement(GameTestHelper helper) {
        BenchmarkState state = new BenchmarkState(32);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> resolveParts(helper, state))
                .thenExecute(() -> configureFrequency(helper, state))
                .thenExecute(() -> {
                    PatternP2PTunnelPart removed = state.outputs.remove(state.outputs.size() - 1);
                    var host = PartHelper.getPartHost(helper.getLevel(), removed.getBlockEntity().getBlockPos());
                    helper.assertTrue(host != null && host.removePart(removed), "Could not remove lifecycle output");
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(state.topology.getOutputs(FREQUENCY).size() == 31,
                        "Topology snapshot retained a removed output"))
                .thenExecute(() -> {
                    BlockPos position = state.input.getBlockEntity().getBlockPos().offset(7, 0, 3);
                    PatternP2PTunnelPart replacement = PartHelper.setPart(
                            helper.getLevel(), position, net.minecraft.core.Direction.UP,
                            null, ModContent.PATTERN_P2P_TUNNEL_OUTPUT.get());
                    helper.assertTrue(replacement != null, "Could not restore lifecycle output");
                    P2PService.get(state.input.getMainNode().getGrid()).updateFreq(replacement, FREQUENCY);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(state.topology.getOutputs(FREQUENCY).size() == 32,
                        "Topology snapshot did not index the restored output"))
                .thenSucceed();
    }

    private static void runTopologyBenchmark(GameTestHelper helper, int endpoints, int width, int depth) {
        BenchmarkState state = new BenchmarkState(endpoints);
        MinecraftServer server = helper.getLevel().getServer();
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> state.baselineTicks = snapshotTickTimes(server))
                .thenExecute(() -> resolveParts(helper, state))
                .thenExecute(() -> configureFrequency(helper, state))
                .thenIdle(20)
                .thenExecuteFor(SAMPLE_TICKS, () -> state.stableQueries.add(queryOutputs(state, false)))
                .thenExecute(() -> state.loadedTicks = snapshotTickTimes(server))
                .thenExecuteFor(INVALIDATION_TICKS, () -> state.rebuildQueries.add(queryOutputs(state, true)))
                .thenExecute(() -> writeResults(state))
                .thenSucceed();
    }

    private static void resolveParts(GameTestHelper helper, BenchmarkState state) {
        BuiltTopology built = BUILT_TOPOLOGIES.remove(state.endpoints);
        if (built == null || built.input == null || built.outputs.size() != state.endpoints) {
            helper.fail("Benchmark plot did not expose " + state.endpoints + " parts");
            return;
        }
        state.input = built.input;
        state.outputs.addAll(built.outputs);
    }

    private static void configureFrequency(GameTestHelper helper, BenchmarkState state) {
        var grid = state.input.getMainNode().getGrid();
        if (grid == null || !state.input.getMainNode().hasGridBooted()) {
            helper.fail("Benchmark grid did not boot");
            return;
        }
        var p2p = P2PService.get(grid);
        p2p.updateFreq(state.input, FREQUENCY);
        for (PatternP2PTunnelPart output : state.outputs) {
            p2p.updateFreq(output, FREQUENCY);
        }
        state.topology = grid.getService(PatternP2PTopologyGridService.class);
        state.topology.topologyChanged();
        int indexed = state.topology.getOutputs(FREQUENCY).size();
        if (indexed != state.endpoints) {
            int p2pOutputs = P2PService.get(grid).getOutputs(FREQUENCY, PatternP2PTunnelPart.class).toList().size();
            helper.fail("Expected " + state.endpoints + " indexed outputs, got " + indexed
                    + "; inputFrequency=" + state.input.getFrequency()
                    + "; firstOutputFrequency=" + state.outputs.get(0).getFrequency()
                    + "; gridSize=" + grid.size()
                    + "; p2pOutputs=" + p2pOutputs
                    + "; inputActive=" + state.input.getMainNode().isActive()
                    + "; outputActive=" + state.outputs.get(0).getMainNode().isActive());
        }
    }

    private static long queryOutputs(BenchmarkState state, boolean invalidate) {
        if (invalidate) {
            state.topology.topologyChanged();
        }
        long started = System.nanoTime();
        int size = state.topology.getOutputs(FREQUENCY).size();
        long elapsed = System.nanoTime() - started;
        if (size != state.endpoints) {
            throw new IllegalStateException("Topology output count changed: " + size);
        }
        return elapsed;
    }

    private static long[] snapshotTickTimes(MinecraftServer server) {
        return Arrays.stream(server.getTickTimesNanos()).filter(value -> value > 0).toArray();
    }

    private static void writeResults(BenchmarkState state) {
        try {
            Files.createDirectories(RESULTS.getParent());
            boolean header = Files.notExists(RESULTS);
            StringBuilder csv = new StringBuilder();
            if (header) {
                csv.append("version,endpoints,phase,samples,p50_ms,p95_ms,p99_ms,mean_ms,max_ms\n");
            }
            append(csv, state.endpoints, "ae_baseline_tick", state.baselineTicks);
            append(csv, state.endpoints, "mod_loaded_tick", state.loadedTicks);
            append(csv, state.endpoints, "stable_query", toArray(state.stableQueries));
            append(csv, state.endpoints, "snapshot_rebuild", toArray(state.rebuildQueries));
            Files.writeString(RESULTS, csv.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write performance results", e);
        }
    }

    private static void append(StringBuilder csv, int endpoints, String phase, long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0) / 1_000_000.0;
        csv.append("1.21.1,").append(endpoints).append(',').append(phase).append(',')
                .append(sorted.length).append(',')
                .append(ms(percentile(sorted, 0.50))).append(',')
                .append(ms(percentile(sorted, 0.95))).append(',')
                .append(ms(percentile(sorted, 0.99))).append(',')
                .append(String.format(java.util.Locale.ROOT, "%.6f", mean)).append(',')
                .append(ms(sorted.length == 0 ? 0 : sorted[sorted.length - 1])).append('\n');
    }

    private static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.6f", nanos / 1_000_000.0);
    }

    private static long[] toArray(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    @SuppressWarnings("unchecked")
    private static void registerPlot(int endpoints, int width, int depth) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "ae2_batchcraft", "topologyperformancegametest.topology_" + endpoints);
        try {
            TestPlots.getPlotIds();
            Field plotsField = TestPlots.class.getDeclaredField("plots");
            plotsField.setAccessible(true);
            Map<ResourceLocation, Consumer<PlotBuilder>> plots =
                    (Map<ResourceLocation, Consumer<PlotBuilder>>) plotsField.get(null);
            plots.put(id, plot -> {
                plot.cable("[0," + (width - 1) + "] 0 [0," + (depth - 1) + "]");
                plot.addPostBuildAction((level, player, origin) -> {
                    PatternP2PTunnelPart input = PartHelper.setPart(
                            level, origin, net.minecraft.core.Direction.NORTH,
                            player, ModContent.PATTERN_P2P_TUNNEL_INPUT.get());
                    List<PatternP2PTunnelPart> outputs = new ArrayList<>();
                    for (int index = 0; index < endpoints; index++) {
                        PatternP2PTunnelPart output = PartHelper.setPart(
                                level, origin.offset(index % width, 0, index / width),
                                net.minecraft.core.Direction.UP, player,
                                ModContent.PATTERN_P2P_TUNNEL_OUTPUT.get());
                        if (output != null) {
                            outputs.add(output);
                        }
                    }
                    BUILT_TOPOLOGIES.put(endpoints, new BuiltTopology(input, outputs));
                });
                plot.creativeEnergyCell(new BlockPos(0, -1, 0));
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register performance plot " + id, e);
        }
    }

    private static final class BenchmarkState {
        private final int endpoints;
        private final List<PatternP2PTunnelPart> outputs = new ArrayList<>();
        private final List<Long> stableQueries = new ArrayList<>();
        private final List<Long> rebuildQueries = new ArrayList<>();
        private PatternP2PTunnelPart input;
        private PatternP2PTopologyGridService topology;
        private long[] baselineTicks = new long[0];
        private long[] loadedTicks = new long[0];

        private BenchmarkState(int endpoints) {
            this.endpoints = endpoints;
        }
    }

    private record BuiltTopology(PatternP2PTunnelPart input, List<PatternP2PTunnelPart> outputs) {
    }
}
