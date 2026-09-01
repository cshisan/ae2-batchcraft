package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductExtractionThroughputSourceTest {
    @Test
    void extractorReusesReturnCapacityWithinBoundedRounds() throws Exception {
        String extractor = source("logic/ProductExtractor.java");

        assertTrue(extractor.contains("while (moved < settings.amount()"));
        assertTrue(extractor.contains("budget.hasRemainingRound()"));
        assertTrue(extractor.contains("budget.consumeSuccessfulRound()"));
        assertTrue(extractor.contains("afterSuccessfulRound.run()"));
        assertTrue(extractor.contains("roundDestinationBlocked = true"));
        assertTrue(extractor.contains("destinationBlocked = roundDestinationBlocked"));
        assertTrue(extractor.contains("destinationBlocked && !budgetExhausted"));
        assertTrue(extractor.contains("if (!roundProgress)"));
        assertTrue(extractor.contains("break;"));
    }

    @Test
    void allExtractionHostsConsumeTheGridBudget() throws Exception {
        String output = source("logic/PatternP2PTunnelOutputLogic.java");
        String unitPort = source("part/PatternP2PUnitPortPart.java");
        String provider = source("mixin/PatternProviderLogicMixin.java");

        assertTrue(output.contains("productExtractionRecovery::queue, budget"));
        assertTrue(unitPort.contains("productExtractionRecovery::queue, budget"));
        assertTrue(provider.contains("ae2bc$extractionRecovery::queue, budget"));
        assertTrue(provider.contains("this::ae2bc$requestReturnInventoryFlush"));
    }

    @Test
    void gridBudgetDefersWithoutBackoffAndPreservesEndpointFairness() throws Exception {
        String service = source("logic/ProductExtractionGridService.java");

        assertTrue(service.contains("ProductExtractionBudget budget = new ProductExtractionBudget()"));
        assertTrue(service.contains("if (budget.isGridExhausted())"));
        assertTrue(service.contains("schedule.schedule(job, tick + 1)"));
        int unservedLoop = service.indexOf("for (; index < dueJobs.size(); index++)");
        int exhaustedLoop = service.indexOf("for (Job job : budgetExhaustedJobs)");
        assertTrue(unservedLoop >= 0 && exhaustedLoop > unservedLoop);
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc").resolve(relativePath));
    }
}
