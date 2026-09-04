package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAllocationModeSourceTest {
    @Test
    void inputPersistsAndDefaultsTheAllocationMode() throws Exception {
        String logic = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PTunnelInputLogic.java"));

        assertTrue(logic.contains("\"TaskAllocationMode\""));
        assertTrue(logic.contains("TaskAllocationMode.fromId(data.getByte(TASK_ALLOCATION_MODE))"));
        assertTrue(logic.contains(": TaskAllocationMode.ROUND_ROBIN"));
        assertTrue(logic.contains("taskAllocationMode.getId()"));
    }

    @Test
    void randomSelectionUsesARandomStartAndRetriesOtherEndpoints() throws Exception {
        String input = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PTunnelInputLogic.java"));

        assertTrue(input.contains("getAvailableTaskEndpoints() : outputs"));
        assertTrue(input.contains("selectRandomEndpoint(candidates, pattern, metadata, inputs)"));
        assertTrue(input.contains("random.nextInt(candidates.size())"));
        assertTrue(input.contains("for (int offset = 0; offset < candidates.size(); offset++)"));
        assertTrue(input.contains("context.reassignRoundAllocation"));
        assertTrue(input.contains("for (int attempt = 0; attempt < size; attempt++)"));
    }
}
