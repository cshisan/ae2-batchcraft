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
    void randomSelectionIsWithReplacementAndAttemptsOneEndpoint() throws Exception {
        String selector = Files.readString(Path.of(
                "../shared/src/main/java/cn/ae2bc/core/dispatch/TaskEndpointSelector.java"));
        String input = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PTunnelInputLogic.java"));

        assertTrue(selector.contains("int index = boundedRandom.applyAsInt(size)"));
        assertTrue(selector.contains("return attempt.test(index) ? index : -1"));
        assertTrue(input.contains("getAvailableTaskEndpoints() : outputs"));
    }
}
