package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderOwnedBatchDispatchSourceTest {
    @Test
    void fullDispatchRemainsSingleEndpointFastPath() throws Exception {
        String input = source("logic/PatternP2PTunnelInputLogic.java");
        assertTrue(input.contains("pushPatternComplete(pattern, metadata, inputs, outputs, size)"));
        assertTrue(input.contains("output.tryAcceptPattern(pattern, metadata, inputs, actionSource)"));
        assertFalse(input.contains("totalCapacity < totalUnits"));
    }

    @Test
    void batchRemainderStaysWithProviderAndStorageIsControlled() throws Exception {
        String input = source("logic/PatternP2PTunnelInputLogic.java");
        assertTrue(input.contains("batchContext = context"));
        assertTrue(input.contains("return false;"));
        assertTrue(input.contains("instanceof PatternProviderLogicHost"));
        assertTrue(input.contains("part.getInputLogic().getBatchStorage()")
                || source("Ae2bcMod.java").contains("part.getInputLogic().getBatchStorage()"));

        String context = source("logic/BatchDispatchContext.java");
        assertTrue(context.contains("roundUnits"));
        assertTrue(context.contains("heldInputs"));
        assertTrue(context.contains("isRoundReady()"));
    }

    @Test
    void completedRoundRemovesZeroCountersAndCannotCrashTheGridTicker() throws Exception {
        String context = source("logic/BatchDispatchContext.java");
        assertTrue(context.contains("boolean startRound(Map<String, Long> allocations)"));
        assertTrue(context.contains("heldInputs[i].removeZeros();"));
        assertTrue(context.contains("holder.removeZeros();"));
        assertTrue(context.contains("entry.getLongValue() > 0"));
        assertFalse(context.contains("Cannot replace an active batch round"));

        String input = source("logic/PatternP2PTunnelInputLogic.java");
        assertTrue(input.contains("if (!context.startRound(roundPlan))"));
    }

    @Test
    void batchSeriesPersistsAndConsecutiveMatchingTasksReuseItsSession() throws Exception {
        String context = source("logic/BatchDispatchContext.java");
        assertTrue(context.contains("tag.putUUID(SESSION_ID, sessionId)"));

        String series = source("logic/BatchDispatchSeries.java");
        assertTrue(series.contains("boolean matches(BatchDispatchContext context)"));
        assertTrue(series.contains("configuredBatchCount == context.configuredBatchCount()"));
        assertTrue(series.contains("taskUnits == context.taskUnits()"));
        assertTrue(series.contains("countersEqual(atomicInputs, context.atomicInputs())"));

        String input = source("logic/PatternP2PTunnelInputLogic.java");
        assertTrue(input.contains("batchSeries.sessionId()"));
        assertTrue(input.contains("hasActiveBatchSession(batchSeries.sessionId())"));
        assertTrue(input.contains("BatchDispatchSeries.from(context)"));
        assertTrue(input.contains("data.put(BATCH_SERIES, batchSeries.write(registries))"));

        String output = source("logic/PatternP2PTunnelOutputLogic.java");
        assertTrue(output.contains("sessionId.equals(batchSessionId)"));
        assertTrue(output.contains("data.putUUID(BATCH_SESSION_ID, batchSessionId)"));
        assertTrue(output.contains("returnBatch.append("));

        String manager = source("logic/PatternP2PUnitManagerLogic.java");
        assertTrue(manager.contains("sessionId.equals(batchSessionId)"));
        assertTrue(manager.contains("data.putUUID(BATCH_SESSION_ID, batchSessionId)"));
        assertTrue(manager.contains("activePattern = pattern.getDefinition()"));
    }

    @Test
    void externalInventoryPlanningUsesAe2StorageWrappersOnly() throws Exception {
        String output = source("logic/PatternP2PTunnelOutputLogic.java");
        assertTrue(output.contains("StackWorldBehaviors.createExternalStorageStrategies"));
        assertTrue(output.contains("Actionable.SIMULATE"));
        assertTrue(output.contains("Actionable.MODULATE"));
        assertFalse(output.contains("MultiSlotInsertPlanner"));
        assertFalse(output.contains("IItemHandler"));
        assertFalse(output.contains("Capabilities.ItemHandler"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc").resolve(relativePath));
    }
}
